/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * Service for displaying prompt dialogs
 */
angular.module('prompt').factory('guacPrompt', ['$injector',
        function guacPrompt($injector) {

    // Required services
    var $log                  = $injector.get('$log');
    var $rootScope            = $injector.get('$rootScope');
    var sessionStorageFactory = $injector.get('sessionStorageFactory');

    var service = {};

    /**
     * Getter/setter which retrieves or sets the current prompt,
     * which may simply be false if no user input is currently required.
     * 
     * @type Function
     */
    var storedPrompt = sessionStorageFactory.create(false);

    /**
     * An action to be provided along with the object sent to showPrompt which
     * closes the currently-shown prompt dialog.
     *
     * @type PromptAction
     */
    service.ACKNOWLEDGE_ACTION = {
        name        : 'APP.ACTION_ACKNOWLEDGE',
        callback    : function acknowledgeCallback() {
            service.showPrompt(false);
        }
    };

    /**
     * Retrieves the current prompt, which may simply be false if no
     * additional information is required.
     * 
     * @type Prompt|Boolean
     */
    service.getPrompt = function getPrompt() {
        return storedPrompt();
    };

    /**
     * Shows or hides the given prompt as a modal dialog. If a prompt
     * is currently shown, no further prompts will be shown until the current
     * one is resolved.
     *
     * @param {Prompt|Boolean|Object} prompt
     *     The prompt to show.
     */
    service.showPrompt = function showPrompt(prompt) {
        if (!storedPrompt() || !prompt)
            storedPrompt(prompt);
    };

    // Hide prompt upon navigation
    $rootScope.$on('$routeChangeSuccess', function() {
        service.showPrompt(false);
    });

    return service;

}]);
