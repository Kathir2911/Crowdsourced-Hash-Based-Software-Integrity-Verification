const path = require('path');
const CopyPlugin = require('copy-webpack-plugin');

module.exports = {
  entry: {
    background: './src/background.ts',
    popup: './src/popup.ts',
    content: './src/content.ts'
  },
  experiments: {
    asyncWebAssembly: true,
  },
  module: {
    rules: [
      {
        test: /\.ts$/,
        use: 'ts-loader',
        exclude: /node_modules/,
      },
      {
        test: /\.wasm$/,
        type: 'webassembly/async',
      },
    ],
  },
  resolve: {
    extensions: ['.ts', '.js', '.wasm'],
  },
  output: {
    filename: '[name].js',
    path: path.resolve(__dirname, 'dist'),
    clean: true,
    webassemblyModuleFilename: '[hash].wasm',
  },
  plugins: [
    new CopyPlugin({
      patterns: [
        {
          from: 'node_modules/blake3/dist/wasm/browser/blake3_js_bg.wasm',
          to: 'blake3_js_bg.wasm'
        }
      ],
    }),
  ],
  optimization: {
    minimize: false, // Keep readable for debugging
  },
  devtool: 'source-map',
};