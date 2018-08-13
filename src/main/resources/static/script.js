angular.module('patternfly.wizard').controller('WizardModalController', ['$scope', '$timeout', '$uibModal', '$rootScope', '$http',
  function ($scope, $timeout, $uibModal, $rootScope, $http) {
    $scope.openWizardModel = function () {
      var wizardDoneListener,
          modalInstance = $uibModal.open({
            animation: true,
            backdrop: 'static',
            templateUrl: 'wizard-container.html',
            controller: 'WizardController',
            size: 'lg'
          });

      var closeWizard = function (e, reason) {
        modalInstance.dismiss(reason);
        wizardDoneListener();
      };

      modalInstance.result.then(function () { }, function () { });

      wizardDoneListener = $rootScope.$on('wizard.done', closeWizard);
    };
    
    $scope.generate = function () {
        document.getElementById('generateButton').disabled = true;
        document.getElementById('generateButton').classList.remove('btn-primary');
        document.getElementById('generateButton').innerText = 'Please wait ... generating app....';        

        var project = {
          "name" : "business-application",
          "version" : "7.10.0-SNAPSHOT",
          "options" : [
        	  "kjar",
        	  "model",
              "service"
          ],
          "capabilities" : [
              "bpm"
          ]
        };

        $http({method: 'POST', url: window.location.protocol + '//' + window.location.host + "/rest/projects",
              headers: {
                  'Content-Type': 'application/json'
              },
              responseType: 'arraybuffer',
              data : project}).
                      success(function(data, status, headers, config) {
                    	  document.getElementById('generateButton').disabled = false;
              			  document.getElementById('generateButton').classList.add('btn-primary');
              			  document.getElementById('generateButton').innerText = 'Generate preconfigured application';
              			  var blob = new Blob([data], {type: "application/zip"});
              			  var a = document.createElement('a');
	                      a.href = URL.createObjectURL(blob);
	                      a.download = project.name + ".zip";
	                      a.click();
                      }).
                      error(function(data, status, headers, config) {
  						
              			
                          alert("Not possible to generate the project");
                          document.getElementById('generateButton').disabled = false;
              			  document.getElementById('generateButton').classList.add('btn-primary');
              			  document.getElementById('generateButton').innerText = 'Generate preconfigured application';
                      });
      };
  }
]);
angular.module('patternfly.wizard').controller('WizardController', ['$scope', '$timeout', '$rootScope',
  function ($scope, $timeout, $rootScope) {


    var initializeWizard = function () {
        $scope.options = {
          model: true,
          kjar: true,
          service: true
        };
        $scope.capabilities = {
          bpm: true,
          brm: false,
          planner: false
        };
      $scope.data = {
        name: '',
        version: '',
        lorem: 'default setting',
        options : $scope.options,
        capabilities : $scope.capabilities
      };

      $scope.secondaryLoadInformation = 'Loading Business Application wizard... please wait';

      $scope.wizardReady = true;

      $scope.nextButtonTitle = "Next >";
    };

    var startDeploy = function () {

      $scope.deployInProgress = true;
      
      var options = [];
      var capabilities = [];
      if ($scope.data.options.kjar == true) {
          options.push('kjar');
      }
      if ($scope.data.options.model == true) {
          options.push('model');
      }
      if ($scope.data.options.service == true) {
          options.push('service');
      }

      if ($scope.data.capabilities.bpm == true) {
          capabilities.push('bpm');
      }
      if ($scope.data.capabilities.brm == true) {
          capabilities.push('brm');
      }
      if ($scope.data.capabilities.planner == true) {
          capabilities.push('planner');
      }
      $scope.project = {
        "name" : $scope.data.name,
        "version" : $scope.data.version,
        "options" : options,
        "capabilities" : capabilities
      };
      
    };

    $scope.data = {};

    $scope.nextCallback = function (step) {
      // call startdeploy after deploy button is clicked on review-summary tab
      if (step.stepId === 'review-summary') {
        startDeploy();
      }
      return true;
    };
    $scope.backCallback = function (step) {
      return true;
    };

    $scope.stepChanged = function (step, index) {
      if (step.stepId === 'review-summary') {
        $scope.nextButtonTitle = "Generate";
      } else if (step.stepId === 'review-progress') {
        $scope.nextButtonTitle = "Close";
      } else {
        $scope.nextButtonTitle = "Next >";
      }
    };

    $scope.cancelDeploymentWizard = function () {
      $rootScope.$emit('wizard.done', 'cancel');
    };

    $scope.finishedWizard = function () {
      $rootScope.$emit('wizard.done', 'done');
      return true;
    };

    initializeWizard();
   }
]);

angular.module('patternfly.wizard').controller('DetailsGeneralController', ['$rootScope', '$scope',
  function ($rootScope, $scope) {
    'use strict';

    $scope.reviewTemplate = "review-template.html";
    $scope.detailsGeneralComplete = false;
    $scope.focusSelectors = ['#new-name'];
    $scope.onShow = function() { };

    $scope.updateName = function() {
      $scope.detailsGeneralComplete = angular.isDefined($scope.data.name) && $scope.data.name.length > 0 && angular.isDefined($scope.data.version) && $scope.data.version.length > 0;
    };
  }
]);

angular.module('patternfly.wizard').controller('DetailsReviewController', ['$rootScope', '$scope',
  function ($rootScope, $scope) {
    'use strict';

    // Find the data!
    var next = $scope;
    while (angular.isUndefined($scope.data)) {
      next = next.$parent;
      if (angular.isUndefined(next)) {
        $scope.data = {};
      } else {
        $scope.data = next.$ctrl.wizardData;
      }
    }
  }
]);

angular.module('patternfly.wizard').controller('SecondStepController', ['$rootScope', '$scope',
  function ($rootScope, $scope) {
    'use strict';

    $scope.focusSelectors = ['.invalid-classname', '#step-two-new-lorem'];
  }
]);

angular.module('patternfly.wizard').controller('SummaryController', ['$rootScope', '$scope', '$timeout',
  function ($rootScope, $scope, $timeout) {
    'use strict';
    $scope.pageShown = false;

    $scope.onShow = function () {
      $scope.pageShown = true;
      $timeout(function () {
        $scope.pageShown = false;  // done so the next time the page is shown it updates
      });
    }
  }
]);

angular.module('patternfly.wizard').controller('DeploymentController', ['$rootScope', '$scope', '$timeout', '$http',
  function ($rootScope, $scope, $timeout, $http) {
    'use strict';
    $scope.onShow = function() {
    $scope.deploymentComplete = false;
    $http({method: 'POST', url: window.location.protocol + '//' + window.location.host + "/rest/projects",
          headers: {
              'Content-Type': 'application/json'
          },
          responseType: 'arraybuffer',
          data : $scope.project}).
                  success(function(data, status, headers, config) {                    	
          			
          			$scope.deploymentComplete = true;
          			
                  	var blob = new Blob([data], {type: "application/zip"});
                      var a = document.createElement('a');
                      a.href = URL.createObjectURL(blob);
                      a.download = $scope.project.name + ".zip";
                      a.click();
                      
                  }).
                  error(function(data, status, headers, config) {
                      alert("Not possible to generate the project")
                      $scope.deploymentComplete = true;
                  });
    };
  }
]);
