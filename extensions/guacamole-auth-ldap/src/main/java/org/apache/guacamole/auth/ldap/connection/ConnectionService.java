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

package org.apache.guacamole.auth.ldap.connection;

import com.google.inject.Inject;
import com.novell.ldap.LDAPAttribute;
import com.novell.ldap.LDAPConnection;
import com.novell.ldap.LDAPEntry;
import com.novell.ldap.LDAPException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import org.apache.guacamole.auth.ldap.LDAPAuthenticationProvider;
import org.apache.guacamole.auth.ldap.ConfigurationService;
import org.apache.guacamole.auth.ldap.EscapingService;
import org.apache.guacamole.GuacamoleException;
import org.apache.guacamole.GuacamoleServerException;
import org.apache.guacamole.auth.ldap.ObjectQueryService;
import org.apache.guacamole.auth.ldap.group.UserGroupService;
import org.apache.guacamole.auth.ldap.user.LDAPAuthenticatedUser;
import org.apache.guacamole.net.auth.AuthenticatedUser;
import org.apache.guacamole.net.auth.Connection;
import org.apache.guacamole.net.auth.GuacamoleProxyConfiguration;
import org.apache.guacamole.net.auth.GuacamoleProxyConfiguration.EncryptionMethod;
import org.apache.guacamole.net.auth.TokenInjectingConnection;
import org.apache.guacamole.net.auth.simple.SimpleConnection;
import org.apache.guacamole.protocol.GuacamoleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for querying the connections available to a particular Guacamole
 * user according to an LDAP directory.
 */
public class ConnectionService {

    /**
     * Logger for this class.
     */
    private static final Logger logger = LoggerFactory.getLogger(ConnectionService.class);

    /**
     * Service for escaping parts of LDAP queries.
     */
    @Inject
    private EscapingService escapingService;

    /**
     * Service for retrieving LDAP server configuration information.
     */
    @Inject
    private ConfigurationService confService;

    /**
     * Service for executing LDAP queries.
     */
    @Inject
    private ObjectQueryService queryService;

    /**
     * Service for retrieving user groups.
     */
    @Inject
    private UserGroupService userGroupService;
    
    /**
     * The LDAP attribute to query to retrieve parameters.
     */
    public static final String LDAP_PARAMETER_ATTRIBUTE = "guacConfigParameter";
    
    /**
     * The LDAP attribute to query to retrieve protocol configuration.
     */
    public static final String LDAP_PROTOCOL_ATTRIBUTE = "guacConfigProtocol";
    
    /**
     * The LDAP attribute to query to retrieve proxy configuration.
     */
    public static final String LDAP_PROXY_ATTRIBUTE = "guacConfigProxy";
    
    /**
     * The parameter in LDAP that stores the hostname of the guacd
     * server to use to establish the connection.
     */
    public static final String PROXY_HOST_PARAMETER = "proxy-hostname";
    
    /**
     * The parameter in LDAP that stores the port number of the guacd
     * server to use to establish the connection.
     */
    public static final String PROXY_PORT_PARAMETER = "proxy-port";
    
    /**
     * The parameter in LDAP that stores the encryption type used to
     * talk to guacd to establish the connectoin.
     */
    public static final String PROXY_ENCRYPTION_PARAMETER = "proxy-encryption";

    /**
     * Returns all Guacamole connections accessible to the user currently bound
     * under the given LDAP connection.
     *
     * @param user
     *     The AuthenticatedUser object associated with the user who is
     *     currently authenticated with Guacamole.
     *
     * @param ldapConnection
     *     The current connection to the LDAP server, associated with the
     *     current user.
     *
     * @return
     *     All connections accessible to the user currently bound under the
     *     given LDAP connection, as a map of connection identifier to
     *     corresponding connection object.
     *
     * @throws GuacamoleException
     *     If an error occurs preventing retrieval of connections.
     */
    public Map<String, Connection> getConnections(AuthenticatedUser user,
            LDAPConnection ldapConnection) throws GuacamoleException {

        // Do not return any connections if base DN is not specified
        String configurationBaseDN = confService.getConfigurationBaseDN();
        if (configurationBaseDN == null)
            return Collections.<String, Connection>emptyMap();

        try {

            // Pull the current user DN from the LDAP connection
            String userDN = ldapConnection.getAuthenticationDN();

            // getConnections() will only be called after a connection has been
            // authenticated (via non-anonymous bind), thus userDN cannot
            // possibly be null
            assert(userDN != null);

            // Get the search filter for finding connections accessible by the
            // current user
            String connectionSearchFilter = getConnectionSearchFilter(userDN, ldapConnection);

            // Find all Guacamole connections for the given user by
            // looking for direct membership in the guacConfigGroup
            // and possibly any groups the user is a member of that are
            // referred to in the seeAlso attribute of the guacConfigGroup.
            List<LDAPEntry> results = queryService.search(ldapConnection, configurationBaseDN, connectionSearchFilter);

            // Return a map of all readable connections
            return queryService.asMap(results, (entry) -> {

                // Get common name (CN)
                LDAPAttribute cn = entry.getAttribute("cn");
                if (cn == null) {
                    logger.warn("guacConfigGroup is missing a cn.");
                    return null;
                }

                // Get associated protocol
                LDAPAttribute protocol = entry.getAttribute(LDAP_PROTOCOL_ATTRIBUTE);
                if (protocol == null) {
                    logger.warn("guacConfigGroup \"{}\" is missing the "
                              + "required \"guacConfigProtocol\" attribute.",
                            cn.getStringValue());
                    return null;
                }

                // Set protocol
                GuacamoleConfiguration config = new GuacamoleConfiguration();
                
                GuacamoleProxyConfiguration proxyConfig;
                try {
                    proxyConfig = confService.getDefaultGuacamoleProxyConfiguration();
                }
                catch (GuacamoleException e) {
                    logger.warn("Could not retrieve Guacamole proxy configuration.",
                            e.getMessage());
                    logger.debug("Error when trying to get Guacamole proxy configuration.",
                            e);
                    return null;
                }
                config.setProtocol(protocol.getStringValue());
                
                // Get proxy configuration, if any
                LDAPAttribute proxyAttribute = entry.getAttribute(LDAP_PROXY_ATTRIBUTE);
                if (proxyAttribute != null) {
                    
                    // Retrieve the default proxy parameters.
                    String proxyHost = proxyConfig.getHostname();
                    int proxyPort = proxyConfig.getPort();
                    EncryptionMethod proxySSL = proxyConfig.getEncryptionMethod();
                    
                                        // For each parameter
                    Enumeration<?> parameters = proxyAttribute.getStringValues();
                    while (parameters.hasMoreElements()) {

                        String parameter = (String) parameters.nextElement();

                        // Parse parameter
                        int equals = parameter.indexOf('=');
                        if (equals != -1) {

                            // Parse name
                            String name = parameter.substring(0, equals);
                            String value = parameter.substring(equals+1);
                            
                            // Pull out and set proxy parameters, if present
                            // Otherwise set the parameter.
                            switch(name) {
                                case PROXY_HOST_PARAMETER:
                                    proxyHost = value;
                                    break;
                                case PROXY_PORT_PARAMETER:
                                    proxyPort = Integer.parseInt(value);
                                    break;
                                case PROXY_ENCRYPTION_PARAMETER:
                                    proxySSL = EncryptionMethod.valueOf(value);
                                    break;
                                default:
                                    logger.warn("Invalid proxy configuration item specified.");
                            }

                        }
                    }
                    proxyConfig = new GuacamoleProxyConfiguration(proxyHost, proxyPort, proxySSL);
                }

                // Get parameters, if any
                LDAPAttribute parameterAttribute = entry.getAttribute(LDAP_PARAMETER_ATTRIBUTE);
                if (parameterAttribute != null) {

                    // For each parameter
                    Enumeration<?> parameters = parameterAttribute.getStringValues();
                    while (parameters.hasMoreElements()) {

                        String parameter = (String) parameters.nextElement();

                        // Parse parameter
                        int equals = parameter.indexOf('=');
                        if (equals != -1) {

                            // Parse name and value and set parameter
                            String name = parameter.substring(0, equals);
                            String value = parameter.substring(equals+1);
                            config.setParameter(name, value);

                        }

                    }

                }

                // Store connection using cn for both identifier and name
                String name = cn.getStringValue();
                Connection connection = new SimpleConnection(name, name, config, proxyConfig);
                connection.setParentIdentifier(LDAPAuthenticationProvider.ROOT_CONNECTION_GROUP);

                // Inject LDAP-specific tokens only if LDAP handled user
                // authentication
                if (user instanceof LDAPAuthenticatedUser)
                    connection = new TokenInjectingConnection(connection,
                            ((LDAPAuthenticatedUser) user).getTokens());

                return connection;

            });

        }
        catch (LDAPException e) {
            throw new GuacamoleServerException("Error while querying for connections.", e);
        }

    }

    /**
     * Returns an LDAP search filter which queries all connections accessible
     * by the user having the given DN.
     *
     * @param userDN
     *     DN of the user to search for associated guacConfigGroup connections.
     *
     * @param ldapConnection
     *     LDAP connection to use if additional information must be queried to
     *     produce the filter, such as groups driving RBAC.
     *
     * @return
     *     An LDAP search filter which queries all guacConfigGroup objects
     *     accessible by the user having the given DN.
     *
     * @throws LDAPException
     *     If an error occurs preventing retrieval of user groups.
     *
     * @throws GuacamoleException
     *     If an error occurs retrieving the group base DN.
     */
    private String getConnectionSearchFilter(String userDN,
            LDAPConnection ldapConnection)
            throws LDAPException, GuacamoleException {

        // Create a search filter for the connection search
        StringBuilder connectionSearchFilter = new StringBuilder();

        // Add the prefix to the search filter, prefix filter searches for guacConfigGroups with the userDN as the member attribute value
        connectionSearchFilter.append("(&(objectClass=guacConfigGroup)");
        connectionSearchFilter.append("(|(");
        connectionSearchFilter.append(escapingService.escapeLDAPSearchFilter(
                confService.getMemberAttribute()));
        connectionSearchFilter.append("=");
        connectionSearchFilter.append(escapingService.escapeLDAPSearchFilter(userDN));
        connectionSearchFilter.append(")");

        // Additionally filter by group membership if the current user is a
        // member of any user groups
        List<LDAPEntry> userGroups = userGroupService.getParentUserGroupEntries(ldapConnection, userDN);
        if (!userGroups.isEmpty()) {
            for (LDAPEntry entry : userGroups)
                connectionSearchFilter.append("(seeAlso=").append(escapingService.escapeLDAPSearchFilter(entry.getDN())).append(")");
        }

        // Complete the search filter.
        connectionSearchFilter.append("))");

        return connectionSearchFilter.toString();
    }

}
