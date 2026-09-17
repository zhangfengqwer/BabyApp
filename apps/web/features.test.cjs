const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const context = vm.createContext({ Date, DataView, Map, Set, console, localDateKey: date => date.toISOString().slice(0,10) });
vm.runInContext(fs.readFileSync(__dirname + '/features.js', 'utf8'), context);
test('1904 movie zero time and future values are rejected',()=>{
  assert.equal(context.validCaptureDate(new Date('1904-01-01T00:00:00Z')), null);
  assert.equal(context.validCaptureDate(new Date('2099-01-01T00:00:00Z')), null);
});
test('mixed dates create separate groups and unknown dates use manual fallback',()=>{
  const groups=context.planWebGroups([{capturedAt:new Date('2026-09-01T10:00:00Z')},{capturedAt:new Date('2026-09-02T10:00:00Z')},{capturedAt:null}], '2026-09-03', true);
  assert.deepEqual([...groups.keys()], ['2026-09-01','2026-09-02','2026-09-03']);
});
test('manual mode puts all media in the chosen date',()=>{
  const groups=context.planWebGroups([{capturedAt:new Date('2026-09-01T10:00:00Z')},{capturedAt:null}], '2026-09-03', false);
  assert.equal(groups.size,1);assert.equal(groups.get('2026-09-03').length,2);
});
test('MOV creation time is parsed and zero epoch falls back',()=>{
  const bytes=new Uint8Array(32),view=new DataView(bytes.buffer);
  view.setUint32(0,32);view.setUint32(4,0x6d6f6f76);view.setUint32(8,24);view.setUint32(12,0x6d766864);
  view.setUint32(20,Math.floor(new Date('2026-09-05T10:00:00Z').getTime()/1000)+2082844800);
  assert.equal(context.movieDateInBuffer(view).toISOString(),'2026-09-05T10:00:00.000Z');
  view.setUint32(20,0);assert.equal(context.movieDateInBuffer(view),null);
});
