'use strict';
var behavior = {
  properties: {
    coolSymbolName: String,
    test: {
      type: String,
      computed: 'compute(coolSymbolName)'
    },
    testobserver: {
      type: String,
      observer: 'symbolA'
    },
    testobserverandcomputed: {
      type: Number,
      observer: 'symbolB',
      computed: 'coolSymbolName(compute, symbolA, compute)'
    },
    testnorename: {
      type: Number,
      computed: 'norename(test, testnorename)',
      observer: 'someOtherObserver'
    }
  },
  keyBindings: {
    'up': 'coolSymbolName',
    'down': 'norename',
    'left': 'symbolB'
  },
  kB: {
    'right': 'MY_SYMBOL'
  },
  observers: [
    'MY_SYMBOL(norename, symbolA, symbolB)',
    'unrenamed(symbol, here)'
  ],
  listeners: {
    handleClick: 'coolSymbolName(MY_SYMBOL, testnorename)',
    handleNotRenamed: 'keep(test, testnorename)'
  },
  attached: function() {
    this.listen(this.foo, 'up', 'norename');
    this.listen(this.foo, 'up', 'coolSymbolName');
    this.listen(this.foo, variable, 'coolSymbolName');
    this.listen(this.foo, 'up', coolSymbolName);
    this.$.norename;
    this.$.coolSymbolName;
    this.$.coolSymbolName.symbolA;
    element1.element2.$.coolSymbolName;
    this.addOwnKeyBinding(foo, 'MY_SYMBOL');
    this.addOwnKeyBinding(foo, 'norename');
  }
};
Polymer({
  is: 'polymer-test',
  properties: {
    coolSymbolName: String,
    test: {
      type: String,
      computed: 'compute(coolSymbolName)'
    },
    testobserver: {
      type: String,
      observer: 'symbolA'
    },
    testobserverandcomputed: {
      type: Number,
      observer: 'symbolB',
      computed: 'coolSymbolName(compute, symbolA, compute)'
    },
    testnorename: {
      type: Number,
      computed: 'norename(test, testnorename)',
      observer: 'someOtherObserver'
    }
  },
  keyBindings: {
    'up': 'coolSymbolName',
    'down': 'norename',
    'left': 'symbolB'
  },
  kB: {
    'right': 'MY_SYMBOL'
  },
  observers: [
    'MY_SYMBOL(norename, symbolA, symbolB)',
    'unrenamed(symbol, here)'
  ],
  listeners: {
    handleClick: 'coolSymbolName(MY_SYMBOL, testnorename)',
    handleNotRenamed: 'keep(test, testnorename)'
  },
  untouched1: [
    'MY_SYMBOL(norename, symbolA, symbolB)',
    'unrenamed(symbol, here)'
  ],
  untouched2: {
    test: {
      type: String,
      computed: 'compute(coolSymbolName)'
    }
  },
  attached: function() {
    this.listen(this.foo, 'up', 'norename');
    this.listen(this.foo, 'up', 'coolSymbolName');
    this.listen(this.foo, variable, 'coolSymbolName');
    this.listen(this.foo, 'up', coolSymbolName);
    this.$.norename;
    this.$.coolSymbolName;
    this.$.coolSymbolName.symbolA;
    element1.element2.$.coolSymbolName;
    this.addOwnKeyBinding(foo, 'MY_SYMBOL');
    this.addOwnKeyBinding(foo, 'norename');
  }
});
