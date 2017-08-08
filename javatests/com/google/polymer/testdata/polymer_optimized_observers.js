var behavior = {
  observers:
      'MY_SYMBOL(norename, symbolA, symbolB);unrenamed(symbol, here)'.split(';')
};
Polymer({
  is: 'polymer-test',
  observers:
      'MY_SYMBOL(norename, symbolA, symbolB);unrenamed(symbol, here)'.split(';')
});
