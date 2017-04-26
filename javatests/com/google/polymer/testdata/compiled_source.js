'use strict';Polymer({is:"x-foo",properties:{bar:String},listeners:{"span.click":"onSpanClick_"},a:function(){return"bar: "+this.bar},b:function(a){console.log(a.type)}});
