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
 * A directive which displays the contents of a connection group.
 */
angular.module('home').directive('guacActiveConnections', [function guacActiveConnections() {

    return {
        restrict: 'E',
        replace: true,
        scope: {

            /**
             * The root connection groups to display, and all visible
             * descendants, as a map of data source identifier to the root
             * connection group within that data source. Recent connections
             * will only be shown if they exist within this hierarchy,
             * regardless of their existence within the history.
             *
             * @type Object.<String, ConnectionGroup>
             */
            rootGroups : '='

        },

        templateUrl: 'app/home/templates/guacActiveConnections.html',
        controller: ['$scope', '$injector', function guacActiveConnectionsController($scope, $injector) {

            // Required types
            var ActiveConnection = $injector.get('ActiveConnection');
            var ClientIdentifier = $injector.get('ClientIdentifier');

            // Required services
            var $log = $injector.get('$log');
            var guacClientManager = $injector.get('guacClientManager');

            /**
             * Array of all known and visible active connections.
             *
             * @type ActiveConnection[]
             */
            $scope.activeConnections = [];

            /**
             * Returns whether recent connections are available for display.
             * Note that, for the sake of this directive, recent connections
             * include any currently-active connections, even if they are not
             * yet in the history.
             *
             * @returns {Boolean}
             *     true if active connections are present, false
             *     otherwise.
             */
            $scope.hasActiveConnections = function hasActiveConnections() {
                $log.debug('There are ' + $scope.activeConnections.length + ' active connections.');
                return !!($scope.activeConnections.length);
            };

            /**
             * Map of all visible objects, connections or connection groups, by
             * object identifier.
             *
             * @type Object.<String, Connection|ConnectionGroup>
             */
            var visibleObjects = {};

            /**
             * Adds the given connection to the internal set of visible
             * objects.
             *
             * @param {String} dataSource
             *     The identifier of the data source associated with the
             *     given connection group.
             *
             * @param {Connection} connection
             *     The connection to add to the internal set of visible objects.
             */
            var addVisibleConnection = function addVisibleConnection(dataSource, connection) {
                
                $log.debug('Adding visible connection ' + connection.identifier);

                // Add given connection to set of visible objects
                visibleObjects[ClientIdentifier.toString({
                    dataSource : dataSource,
                    type       : ClientIdentifier.Types.CONNECTION,
                    id         : connection.identifier
                })] = connection;

            };

            /**
             * Adds the given connection group to the internal set of visible
             * objects, along with any descendants.
             *
             * @param {String} dataSource
             *     The identifier of the data source associated with the
             *     given connection group.
             *
             * @param {ConnectionGroup} connectionGroup
             *     The connection group to add to the internal set of visible
             *     objects, along with any descendants.
             */
            var addVisibleConnectionGroup = function addVisibleConnectionGroup(dataSource, connectionGroup) {

                $log.debug('Adding visible connection group ' + connectionGroup.identifier);
                
                // Add given connection group to set of visible objects
                visibleObjects[ClientIdentifier.toString({
                    dataSource : dataSource,
                    type       : ClientIdentifier.Types.CONNECTION_GROUP,
                    id         : connectionGroup.identifier
                })] = connectionGroup;

                // Add all child connections
                if (connectionGroup.childConnections)
                    connectionGroup.childConnections.forEach(function addChildConnection(child) {
                        $log.debug('Adding child connection...');
                        addVisibleConnection(dataSource, child);
                    });

                // Add all child connection groups
                if (connectionGroup.childConnectionGroups)
                    connectionGroup.childConnectionGroups.forEach(function addChildConnectionGroup(child) {
                        $log.debug('Adding child connection group.');
                        addVisibleConnectionGroup(dataSource, child);
                    });

            };

            // Update visible objects when root groups are set
            $scope.$watch("rootGroups", function setRootGroups(rootGroups) {

                // Clear connection arrays
                $scope.activeConnections = [];

                // Produce collection of visible objects
                visibleObjects = {};
                if (rootGroups) {
                    angular.forEach(rootGroups, function addConnectionGroup(rootGroup, dataSource) {
                        addVisibleConnectionGroup(dataSource, rootGroup);
                    });
                }

                var managedClients = guacClientManager.getManagedClients();
                $log.debug('There are ' + managedClients.length + ' managed clients.');

                // Add all active connections
                for (var id in managedClients) {
                    $log.debug('Looking at managedClient ' + id);

                    // Get corresponding managed client
                    var client = managedClients[id];

                    // Add active connections for clients with associated visible objects
                    if (id in visibleObjects) {

                        var object = visibleObjects[id];
                        $scope.activeConnections.push(new ActiveConnection(object.name, client));

                    }

                }

            }); // end rootGroup scope watch

        }]

    };
}]);
