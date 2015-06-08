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
  observers: [
    'MY_SYMBOL(norename, symbolA, symbolB)',
    'unrenamed(symbol, here)'
  ],
  listeners: {
    handleClick: 'coolSymbolName(MY_SYMBOL, testnorename)',
    handleNotRenamed: 'keep(test, testnorename)'
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
  }
});
