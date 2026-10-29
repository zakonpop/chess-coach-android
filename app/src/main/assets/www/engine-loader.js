// Stockfish is downloaded once to the app's private storage on first launch.
// All engine resources are loaded from the same local app origin.
self.Module = {
  locateFile: function (path) {
    if (path.endsWith('.wasm')) return './stockfish-19-lite-single.wasm';
    return './' + path;
  }
};
importScripts('./stockfish-19-lite-single.js');
