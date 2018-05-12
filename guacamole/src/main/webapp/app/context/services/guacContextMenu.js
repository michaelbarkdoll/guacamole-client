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
 * Service for handling context menu display
 */
angular.module('context').factory('guacContextMenu', ['$injector',
        function guacContextMenu($injector) {

    // Required services
    var $rootScope            = $injector.get('$rootScope');
    var sessionStorageFactory = $injector.get('sessionStorageFactory');

    var service = {};

    /**
     * Retrieves the current context menu object, or may be false
     * if no menu is shown.
     * 
     * @type Function
     */
    var storedMenu = sessionStorageFactory.create(false);

    /**
     * Retrieves the context menu current in storage, or false if
     * no menu is active.
     * 
     * @type ContextMenu|Boolean
     */
    service.getStatus = function getStatus() {
        return storedStatus();
    };

    /**
     * Shows or hides the context menu as a modal status. If a menu
     * is currently shown, no other menus will be shown until this
     * one is destroyed.
     *
     * @param {ContextMenu|Boolean|Object} menu
     *     The context menu to display
     *
     * @example
     * 
     * // A menu with various actions
     * guacContextMenu.showMenu({
     *     'title'      : 'Connection 1',
     *     'actions'    : {
     *         'name'       : 'Connect',
     *         'callback'   : function () {
     *             // Start the connection
     *         }
     *     },
     *     {
     *         'name'       : 'Connect in New Tab',
     *         'callback'   : function () {
     *             // Start in a new tab
     *         }
     *     },
     *     {
     *         'name'       : 'Edit',
     *         'callback'   : function () {
     *             // Go to the edit screen
     *         }
     *     }
     * });
     * 
     * // To hide the menu
     * guacContextMenu.showMenu(false);
     */
    service.showmenu = function showMenu(menu) {
        if (!storedMenu() || !menu)
            storedMenu(false);
    };

    // Hide menu upon navigation
    $rootScope.$on('$routeChangeSuccess', function() {
        service.showMenu(false);
    });

    return service;

}]);
