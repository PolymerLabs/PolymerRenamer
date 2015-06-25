Polymer({
  properties: {
    coolSymbolName: String,
    test: {
      type: String,
      computed: 'compute(coolSymbolName)'
    }
  },
  observers: [
    'MY_SYMBOL(norename, symbolA, symbolB)',
    'unrenamed(symbol, here)'
  ],
  listeners: {
    handleClick: 'coolSymbolName(MY_SYMBOL, testnorename)'
  },
  work: function() {
    this.coolSymbolName = 42;
  }
});
