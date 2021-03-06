/* jshint node:true */
define(['intern'], function (intern) {
  var useBrowserStack = intern.args.useBrowserStack,
      tunnel = useBrowserStack ? 'BrowserStackTunnel' : 'NullTunnel';

  return {
    excludeInstrumentation: /(((test|third-party|node_modules)\/)|(templates.js$))/,

    defaultTimeout: 60 * 1000,

    reporters: [
      { id: 'Runner' },
      { id: 'Lcov' },
      { id: 'LcovHtml', directory: 'target/web-tests' }
    ],

    suites: [
      'test/unit/application.spec',
      'test/unit/issue.spec'
    ],

    functionalSuites: [
      'test/medium/users.spec',
      'test/medium/issues.spec',
      'test/medium/update-center.spec',
      'test/medium/computation.spec',
      'test/medium/coding-rules.spec',
      'test/medium/custom-measures.spec'
    ],

    tunnel: tunnel,
    environments: [
      { browserName: 'firefox' }
    ]
  };
});
