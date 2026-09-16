package family.babyhome.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import family.babyhome.data.family.FamilyRepository
import family.babyhome.data.network.FamilyUser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

data class FamilyState(val members:List<FamilyUser> = emptyList(),val canManage:Boolean=false,val busy:Boolean=true,val error:String?=null)
@HiltViewModel
class FamilyViewModel @Inject constructor(private val repository:FamilyRepository):ViewModel(){
    private val mutable=MutableStateFlow(FamilyState())
    val state=mutable.asStateFlow()
    init { reload() }
    private fun action(block:suspend ()->Unit) = viewModelScope.launch {
        mutable.update { it.copy(busy=true,error=null) }
        try{block()}catch(e:Exception){if(e is CancellationException)throw e;mutable.update{it.copy(error="操作失败：请检查网络、用户名是否已存在及管理权限")}}finally{mutable.update{it.copy(busy=false)}}
    }
    private suspend fun load(){val page=repository.load();mutable.update{it.copy(members=page.members,canManage=page.canManage)}}
    fun reload(){action{load()}}
    fun save(id:String?,username:String,relationship:String){if(state.value.busy)return;action{if(id==null)repository.create(username,relationship)else repository.edit(id,relationship);load()}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(onBack:()->Unit,viewModel:FamilyViewModel=hiltViewModel()){
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<FamilyUser?>(null)}
    var username by remember{mutableStateOf("")}
    var relationship by remember{mutableStateOf("")}
    if(dialog) AlertDialog(onDismissRequest={dialog=false},title={Text(if(editing==null)"添加家人" else "编辑家庭关系")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            OutlinedTextField(username,{username=it.take(64)},enabled=editing==null,label={Text("用户名 / 手机号")},supportingText={Text("手机号无需验证；也可使用英文或数字用户名")},singleLine=true)
            OutlinedTextField(relationship,{relationship=it.take(32)},label={Text("家庭关系")},supportingText={Text("上传与评论时显示，例如爸爸、妈妈")},singleLine=true)
            listOf(listOf("爸爸","妈妈","爷爷"),listOf("奶奶","外公","外婆")).forEach{row->Row{row.forEach{label->TextButton(onClick={relationship=label},modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text(label)}}}}
        }
    },confirmButton={TextButton(onClick={viewModel.save(editing?.id,username,relationship);dialog=false},enabled=relationship.isNotBlank()&&(editing!=null||username.matches(Regex("[a-zA-Z0-9_.-]{3,64}")))){Text("保存")}},dismissButton={TextButton(onClick={dialog=false}){Text("取消")}})
    Scaffold(topBar={TopAppBar(title={Text("家庭成员")},navigationIcon={TextButton(onClick=onBack){Text("返回")}})},floatingActionButton={if(state.canManage)ExtendedFloatingActionButton(onClick={editing=null;username="";relationship="";dialog=true},text={Text("添加家人")},icon={Text("＋")})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp,16.dp,16.dp,96.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{Text("一起记录宝宝的成长",style=MaterialTheme.typography.titleLarge);Text("每部手机选择自己的身份，上传和评论时显示家庭关系。",Modifier.padding(top=8.dp))}
            if(state.busy)item{Box(Modifier.fillMaxWidth().padding(16.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.size(24.dp))}}
            items(state.members,key={it.id}){member->
                Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
                    ListItem(headlineContent={Text(member.relationship)},supportingContent={Text("${member.username}${if(member.role=="ADMIN")" · 管理员" else ""}")},trailingContent={if(state.canManage)TextButton(onClick={editing=member;username=member.username;relationship=member.relationship;dialog=true},enabled=!state.busy){Text("编辑")}})
                }
            }
            state.error?.let{message->item{Text(message,color=MaterialTheme.colorScheme.error);TextButton(onClick=viewModel::reload){Text("重试")}}}
        }
    }
}
