'use strict';var behavior = {properties:{coolSymbolName:String, test:{type:String, computed:"compute(b)"}, testobserver:{type:String, observer:"ab"}, testobserverandcomputed:{type:Number, observer:"aC", computed:"b(compute,ab,compute)"}, testnorename:{type:Number, computed:"norename(test,testnorename)", observer:"someOtherObserver"}}, keyBindings:{"up":"b", "down":"norename", "left":"aC"}, kB:{"right":"cd3"}, observers:["cd3(norename,ab,aC)", "unrenamed(symbol,here)"], listeners:{handleClick:"b(cd3,testnorename)", 
handleNotRenamed:"keep(test,testnorename)"}, attached:function() {
  this.listen(this.foo, "up", "norename");
  this.listen(this.foo, "up", "b");
  this.listen(this.foo, variable, "b");
  this.listen(this.foo, "up", coolSymbolName);
  this.$.norename;
  this.$.coolSymbolName;
  this.$.coolSymbolName.symbolA;
  element1.element2.$.coolSymbolName;
  this.addOwnKeyBinding(foo, "cd3");
  this.addOwnKeyBinding(foo, "norename");
}};
Polymer({is:"polymer-test", properties:{coolSymbolName:String, test:{type:String, computed:"compute(b)"}, testobserver:{type:String, observer:"ab"}, testobserverandcomputed:{type:Number, observer:"aC", computed:"b(compute,ab,compute)"}, testnorename:{type:Number, computed:"norename(test,testnorename)", observer:"someOtherObserver"}}, keyBindings:{"up":"b", "down":"norename", "left":"aC"}, kB:{"right":"cd3"}, observers:["cd3(norename,ab,aC)", "unrenamed(symbol,here)"], listeners:{handleClick:"b(cd3,testnorename)", 
handleNotRenamed:"keep(test,testnorename)"}, untouched1:["MY_SYMBOL(norename, symbolA, symbolB)", "unrenamed(symbol, here)"], untouched2:{test:{type:String, computed:"compute(coolSymbolName)"}}, attached:function() {
  this.listen(this.foo, "up", "norename");
  this.listen(this.foo, "up", "b");
  this.listen(this.foo, variable, "b");
  this.listen(this.foo, "up", coolSymbolName);
  this.$.norename;
  this.$.coolSymbolName;
  this.$.coolSymbolName.symbolA;
  element1.element2.$.coolSymbolName;
  this.addOwnKeyBinding(foo, "cd3");
  this.addOwnKeyBinding(foo, "norename");
}});

