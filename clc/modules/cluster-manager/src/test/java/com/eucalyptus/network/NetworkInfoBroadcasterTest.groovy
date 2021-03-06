/*************************************************************************
 * Copyright 2009-2014 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 ************************************************************************/
package com.eucalyptus.network

import com.eucalyptus.cluster.Cluster
import com.eucalyptus.cluster.ClusterConfiguration
import com.eucalyptus.cluster.NICluster
import com.eucalyptus.cluster.NIClusters
import com.eucalyptus.cluster.NIConfiguration
import com.eucalyptus.cluster.NIInstance
import com.eucalyptus.cluster.NIManagedSubnet
import com.eucalyptus.cluster.NIManagedSubnets
import com.eucalyptus.cluster.NIMidonet
import com.eucalyptus.cluster.NINode
import com.eucalyptus.cluster.NINodes
import com.eucalyptus.cluster.NIProperty
import com.eucalyptus.cluster.NISubnet
import com.eucalyptus.cluster.NISubnets
import com.eucalyptus.cluster.NetworkInfo
import com.eucalyptus.network.config.Cluster as ConfigCluster
import com.eucalyptus.network.config.EdgeSubnet
import com.eucalyptus.network.config.ManagedSubnet
import com.eucalyptus.network.config.Midonet
import com.eucalyptus.network.config.NetworkConfiguration
import com.eucalyptus.util.TypeMappers
import com.eucalyptus.compute.common.internal.vm.VmInstance.VmState
import com.google.common.base.Function
import com.google.common.base.Optional
import com.google.common.base.Supplier
import edu.ucsb.eucalyptus.cloud.NodeInfo
import org.junit.BeforeClass
import org.junit.Test

import static org.junit.Assert.*

/**
 *
 */
class NetworkInfoBroadcasterTest {

  @BeforeClass
  static void setup( ) {
    TypeMappers.TypeMapperDiscovery discovery = new TypeMappers.TypeMapperDiscovery()
    discovery.processClass( NetworkInfoBroadcaster.NetworkConfigurationToNetworkInfo )
    discovery.processClass( NetworkInfoBroadcaster.NetworkGroupToNetworkGroupNetworkView )
    discovery.processClass( NetworkInfoBroadcaster.VmInstanceToVmInstanceNetworkView )
    discovery.processClass( NetworkInfoBroadcaster.VpcToVpcNetworkView )
    discovery.processClass( NetworkInfoBroadcaster.SubnetToSubnetNetworkView )
    discovery.processClass( NetworkInfoBroadcaster.DhcpOptionSetToDhcpOptionSetNetworkView )
    discovery.processClass( NetworkInfoBroadcaster.RouteTableToRouteTableNetworkView )
    discovery.processClass( NetworkInfoBroadcaster.NetworkAclToNetworkAclNetworkView )
    discovery.processClass( NetworkInfoBroadcaster.VpcNetworkInterfaceToNetworkInterfaceNetworkView )
    discovery.processClass( NetworkInfoBroadcaster.RouteNetworkViewToNIRoute )
    discovery.processClass( NetworkInfoBroadcaster.NetworkAclEntryNetworkViewToNINetworkAclRule )
    discovery.processClass( NetworkInfoBroadcaster.InternetGatewayToInternetGatewayNetworkView )
  }

  @Test
  void testBasicBroadcast( ) {
    NetworkInfo info = NetworkInfoBroadcaster.buildNetworkConfiguration(
      Optional.of( new NetworkConfiguration(
          instanceDnsDomain: 'eucalyptus.internal',
          instanceDnsServers: [ '1.2.3.4' ],
          publicIps: [ '2.0.0.0-2.0.0.255' ],
          privateIps: [ '10.0.0.0-10.0.0.255' ],
          subnets: [
              new EdgeSubnet(
                  name: 'default',
                  subnet: '10.0.0.0',
                  netmask: '255.255.0.0',
                  gateway: '10.0.1.0'
              ),
              new EdgeSubnet(
                  name: 'global',
                  subnet: '192.168.0.0',
                  netmask: '255.255.0.0',
                  gateway: '192.168.0.1'
              )
          ],
          clusters: [
              new ConfigCluster(
                  name: 'cluster1',
                  subnet: new EdgeSubnet(
                      name: 'default',
                  )
              )
          ]
      ) ),
      new NetworkInfoBroadcaster.NetworkInfoSource( ) {
        @Override Iterable<NetworkInfoBroadcaster.VmInstanceNetworkView> getInstances() {
          [ instance( 'i-00000001', 'cluster1', 'node1', '000000000002', '00:00:00:00:00:00', '2.0.0.0', '10.0.0.0' ) ]
        }
        @Override Iterable<NetworkInfoBroadcaster.NetworkGroupNetworkView> getSecurityGroups() {
          [ group( 'sg-00000001', '000000000002', [], [], [] ) ]
        }
        @Override Iterable<NetworkInfoBroadcaster.VpcNetworkView> getVpcs() {
          []
        }
        @Override Iterable<NetworkInfoBroadcaster.SubnetNetworkView> getSubnets() {
         []
        }
        @Override Iterable<NetworkInfoBroadcaster.DhcpOptionSetNetworkView> getDhcpOptionSets() {
         []
        }
        @Override Iterable<NetworkInfoBroadcaster.NetworkAclNetworkView> getNetworkAcls() {
          []
        }
        @Override Iterable<NetworkInfoBroadcaster.RouteTableNetworkView> getRouteTables() {
          []
        }
        @Override Iterable<NetworkInfoBroadcaster.InternetGatewayNetworkView> getInternetGateways() {
          []
        }
        @Override Iterable<NetworkInfoBroadcaster.NetworkInterfaceNetworkView> getNetworkInterfaces() {
          []
        }
      },
      { [ cluster('cluster1', '6.6.6.6', [ 'node1' ]) ] } as Supplier<List<Cluster>>,
      { '1.1.1.1' } as Supplier<String>,
      { [ '127.0.0.1' ] } as Function<List<String>, List<String>>
    )
    assertEquals( 'basic broadcast', new NetworkInfo(
        configuration: new NIConfiguration(
            properties: [
                new NIProperty( name: 'mode', values: ['EDGE'] ),
                new NIProperty( name: 'publicIps', values: ['2.0.0.0-2.0.0.255'] ),
                new NIProperty( name: 'enabledCLCIp', values: ['1.1.1.1'] ),
                new NIProperty( name: 'instanceDNSDomain', values: ['eucalyptus.internal'] ),
                new NIProperty( name: 'instanceDNSServers', values: ['1.2.3.4'] ),
            ],
            subnets: new NISubnets( name: 'subnets', subnets: [
                new NISubnet(
                    name: '192.168.0.0',
                    properties: [
                        new NIProperty( name: 'subnet', values: ['192.168.0.0'] ),
                        new NIProperty( name: 'netmask', values: ['255.255.0.0'] ),
                        new NIProperty( name: 'gateway', values: ['192.168.0.1'] )
                    ]
                )
            ] ),
            clusters: new NIClusters( name: 'clusters', clusters: [
                new NICluster(
                    name: 'cluster1',
                    subnet: new NISubnet(
                        name: '10.0.0.0',
                        properties: [
                            new NIProperty( name: 'subnet', values: ['10.0.0.0'] ),
                            new NIProperty( name: 'netmask', values: ['255.255.0.0'] ),
                            new NIProperty( name: 'gateway', values: ['10.0.1.0'] )
                        ]
                    ),
                    properties: [
                        new NIProperty( name: 'enabledCCIp', values: ['6.6.6.6'] ),
                        new NIProperty( name: 'macPrefix', values: ['d0:0d'] ),
                        new NIProperty( name: 'privateIps', values: ['10.0.0.0-10.0.0.255'] ),
                    ],
                    nodes: new NINodes( name: 'nodes', nodes: [
                      new NINode(
                          name: 'node1',
                          instanceIds: [ 'i-00000001' ]
                      )
                    ] )
                )
            ] ),
        ),
        instances: [
          new NIInstance(
              name: 'i-00000001',
              ownerId: '000000000002',
              macAddress: '00:00:00:00:00:00',
              publicIp: '2.0.0.0',
              privateIp: '10.0.0.0',
              securityGroups: [],
          )
        ],
        securityGroups: [ ]
    ), info )
  }

  @Test
  void testBroadcastDefaults( ) {
    NetworkInfo info = NetworkInfoBroadcaster.buildNetworkConfiguration(
        Optional.of( new NetworkConfiguration(
            publicIps: [ '2.0.0.0-2.0.0.255' ],
            privateIps: [ '10.0.0.0-10.0.0.255' ],
            subnets: [
                new EdgeSubnet(
                    subnet: '10.0.0.0',
                    netmask: '255.255.0.0',
                    gateway: '10.0.1.0'
                )
            ],
            clusters: [
                new ConfigCluster(
                    name: 'cluster1'
                )
            ]
        ) ),
        new NetworkInfoBroadcaster.NetworkInfoSource( ) {
          @Override Iterable<NetworkInfoBroadcaster.VmInstanceNetworkView> getInstances() {
            [ instance( 'i-00000001', 'cluster1', 'node1', '000000000002', '00:00:00:00:00:00', '2.0.0.0', '10.0.0.0' ) ]
          }
          @Override Iterable<NetworkInfoBroadcaster.NetworkGroupNetworkView> getSecurityGroups() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.VpcNetworkView> getVpcs() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.SubnetNetworkView> getSubnets() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.DhcpOptionSetNetworkView> getDhcpOptionSets() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.NetworkAclNetworkView> getNetworkAcls() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.RouteTableNetworkView> getRouteTables() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.InternetGatewayNetworkView> getInternetGateways() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.NetworkInterfaceNetworkView> getNetworkInterfaces() {
            []
          }
        },
        { [ cluster('cluster1', '6.6.6.6', [ 'node1' ]) ] } as Supplier<List<Cluster>>,
        { '1.1.1.1' } as Supplier<String>,
        { [ '127.0.0.1' ] } as Function<List<String>, List<String>>
    )
    assertEquals( 'broadcast defaults', new NetworkInfo(
        configuration: new NIConfiguration(
            properties: [
                new NIProperty( name: 'mode', values: ['EDGE'] ),
                new NIProperty( name: 'publicIps', values: ['2.0.0.0-2.0.0.255'] ),
                new NIProperty( name: 'enabledCLCIp', values: ['1.1.1.1'] ),
                new NIProperty( name: 'instanceDNSDomain', values: ['eucalyptus.internal'] ),
                new NIProperty( name: 'instanceDNSServers', values: ['127.0.0.1'] ),
            ],
            clusters: new NIClusters( name: 'clusters', clusters: [
                new NICluster(
                    name: 'cluster1',
                    subnet: new NISubnet(
                        name: '10.0.0.0',
                        properties: [
                            new NIProperty( name: 'subnet', values: ['10.0.0.0'] ),
                            new NIProperty( name: 'netmask', values: ['255.255.0.0'] ),
                            new NIProperty( name: 'gateway', values: ['10.0.1.0'] )
                        ]
                    ),
                    properties: [
                        new NIProperty( name: 'enabledCCIp', values: ['6.6.6.6'] ),
                        new NIProperty( name: 'macPrefix', values: ['d0:0d'] ),
                        new NIProperty( name: 'privateIps', values: ['10.0.0.0-10.0.0.255'] ),
                    ],
                    nodes: new NINodes( name: 'nodes', nodes: [
                        new NINode(
                            name: 'node1',
                            instanceIds: [ 'i-00000001' ]
                        )
                    ] )
                )
            ] ),
        ),
        instances: [
            new NIInstance(
                name: 'i-00000001',
                ownerId: '000000000002',
                macAddress: '00:00:00:00:00:00',
                publicIp: '2.0.0.0',
                privateIp: '10.0.0.0',
                securityGroups: [],
            )
        ],
        securityGroups: [ ]
    ), info )
  }

  @Test
  void testBroadcastVpcMido( ) {
    NetworkInfo info = NetworkInfoBroadcaster.buildNetworkConfiguration(
        Optional.of( new NetworkConfiguration(
            mode: 'VPCMIDO',
            mido: new Midonet(
                eucanetdHost: 'a-35.qa1.eucalyptus-systems.com',
                gatewayHost: 'a-35.qa1.eucalyptus-systems.com',
                gatewayIP: '10.116.133.77',
                gatewayInterface: 'em1.116',
                publicNetworkCidr: '10.116.0.0/17',
                publicGatewayIP: '10.116.133.67'
            ),
            publicIps: [ '2.0.0.0-2.0.0.255' ],
        ) ),
        new NetworkInfoBroadcaster.NetworkInfoSource( ) {
          @Override Iterable<NetworkInfoBroadcaster.VmInstanceNetworkView> getInstances() {
            [ instance( 'i-00000001', 'cluster1', 'node1', '000000000002', '00:00:00:00:00:00', '2.0.0.0', '10.0.0.0' ) ]
          }
          @Override Iterable<NetworkInfoBroadcaster.NetworkGroupNetworkView> getSecurityGroups() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.VpcNetworkView> getVpcs() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.SubnetNetworkView> getSubnets() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.DhcpOptionSetNetworkView> getDhcpOptionSets() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.NetworkAclNetworkView> getNetworkAcls() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.RouteTableNetworkView> getRouteTables() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.InternetGatewayNetworkView> getInternetGateways() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.NetworkInterfaceNetworkView> getNetworkInterfaces() {
            []
          }
        },
        { [ cluster('cluster1', '6.6.6.6', [ 'node1' ]) ] } as Supplier<List<Cluster>>,
        { '1.1.1.1' } as Supplier<String>,
        { [ '127.0.0.1' ] } as Function<List<String>, List<String>>
    )

    assertEquals( 'broadcast vpc midonet', new NetworkInfo(
        configuration: new NIConfiguration(
            properties: [
                new NIProperty( name: 'mode', values: ['VPCMIDO'] ),
                new NIProperty( name: 'publicIps', values: ['2.0.0.0-2.0.0.255'] ),
                new NIProperty( name: 'enabledCLCIp', values: ['1.1.1.1'] ),
                new NIProperty( name: 'instanceDNSDomain', values: ['eucalyptus.internal'] ),
                new NIProperty( name: 'instanceDNSServers', values: ['127.0.0.1'] ),
            ],
            midonet: new NIMidonet(
                name: 'mido',
                properties: [
                    new NIProperty( name: 'eucanetdHost', values: ['a-35.qa1.eucalyptus-systems.com'] ),
                    new NIProperty( name: 'gatewayHost', values: ['a-35.qa1.eucalyptus-systems.com'] ),
                    new NIProperty( name: 'gatewayIP', values: ['10.116.133.77'] ),
                    new NIProperty( name: 'gatewayInterface', values: ['em1.116'] ),
                    new NIProperty( name: 'publicNetworkCidr', values: ['10.116.0.0/17'] ),
                    new NIProperty( name: 'publicGatewayIP', values: ['10.116.133.67'] ),
                ]
            ),
            clusters: new NIClusters( name: 'clusters', clusters: [
                new NICluster(
                    name: 'cluster1',
                    subnet: new NISubnet(
                        name: '172.31.0.0',
                        properties: [
                            new NIProperty( name: 'subnet', values: ['172.31.0.0'] ),
                            new NIProperty( name: 'netmask', values: ['255.255.0.0'] ),
                            new NIProperty( name: 'gateway', values: ['172.31.0.1'] )
                        ]
                    ),
                    properties: [
                        new NIProperty( name: 'enabledCCIp', values: ['6.6.6.6'] ),
                        new NIProperty( name: 'macPrefix', values: ['d0:0d'] ),
                        new NIProperty( name: 'privateIps', values: ['172.31.0.5'] ),
                    ],
                    nodes: new NINodes( name: 'nodes', nodes: [
                        new NINode(
                            name: 'node1',
                            instanceIds: [ 'i-00000001' ]
                        )
                    ] )
                )
            ] ),
        ),
        instances: [
            new NIInstance(
                name: 'i-00000001',
                ownerId: '000000000002',
                macAddress: '00:00:00:00:00:00',
                publicIp: '2.0.0.0',
                privateIp: '10.0.0.0',
                securityGroups: [],
            )
        ],
        securityGroups: [ ]
    ), info )
  }

  @Test
  void testBroadcastManaged( ) {
    NetworkInfo info = NetworkInfoBroadcaster.buildNetworkConfiguration(
        Optional.of( new NetworkConfiguration(
            mode: 'MANAGED',
            clusters: [
                new ConfigCluster(
                    name: 'cluster1',
                    macPrefix: 'd0:0d'
                )
            ],
            managedSubnet: new ManagedSubnet(
                subnet: '1.101.192.0',
                netmask: '255.255.0.0',
                minVlan: 512,
                maxVlan: 639,
                segmentSize: 32
            ),
            publicIps: [ '2.0.0.0-2.0.0.255' ],
        ) ),
        new NetworkInfoBroadcaster.NetworkInfoSource( ) {
          @Override Iterable<NetworkInfoBroadcaster.VmInstanceNetworkView> getInstances() {
            [ instance( 'i-00000001', 'cluster1', 'node1', '000000000002', '00:00:00:00:00:00', '2.0.0.0', '10.0.0.0' ) ]
          }
          @Override Iterable<NetworkInfoBroadcaster.NetworkGroupNetworkView> getSecurityGroups() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.VpcNetworkView> getVpcs() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.SubnetNetworkView> getSubnets() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.DhcpOptionSetNetworkView> getDhcpOptionSets() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.NetworkAclNetworkView> getNetworkAcls() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.RouteTableNetworkView> getRouteTables() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.InternetGatewayNetworkView> getInternetGateways() {
            []
          }
          @Override Iterable<NetworkInfoBroadcaster.NetworkInterfaceNetworkView> getNetworkInterfaces() {
            []
          }
        },
        { [ cluster('cluster1', '6.6.6.6', [ 'node1' ]) ] } as Supplier<List<Cluster>>,
        { '1.1.1.1' } as Supplier<String>,
        { [ '127.0.0.1' ] } as Function<List<String>, List<String>>
    )

    assertEquals( 'broadcast managed', new NetworkInfo(
        configuration: new NIConfiguration(
            properties: [
                new NIProperty( name: 'mode', values: ['MANAGED'] ),
                new NIProperty( name: 'publicIps', values: ['2.0.0.0-2.0.0.255'] ),
                new NIProperty( name: 'enabledCLCIp', values: ['1.1.1.1'] ),
                new NIProperty( name: 'instanceDNSDomain', values: ['eucalyptus.internal'] ),
                new NIProperty( name: 'instanceDNSServers', values: ['127.0.0.1'] ),
            ],
            managedSubnet: new NIManagedSubnets(
                name: 'managedSubnet',
                managedSubnet: new NIManagedSubnet(
                    name: "1.101.192.0",
                    properties: [
                        new NIProperty( name: 'subnet', values: ['1.101.192.0'] ),
                        new NIProperty( name: 'netmask', values: ['255.255.0.0'] ),
                        new NIProperty( name: 'minVlan', values: ['512'] ),
                        new NIProperty( name: 'maxVlan', values: ['639'] ),
                        new NIProperty( name: 'segmentSize', values: ['32'] )
                    ]
                )
            ),
            clusters: new NIClusters( name: 'clusters', clusters: [
                new NICluster(
                    name: 'cluster1',
                    properties: [
                        new NIProperty( name: 'enabledCCIp', values: ['6.6.6.6'] ),
                        new NIProperty( name: 'macPrefix', values: ['d0:0d'] ),
                    ],
                    nodes: new NINodes( name: 'nodes', nodes: [
                        new NINode(
                            name: 'node1',
                            instanceIds: [ 'i-00000001' ]
                        )
                    ] )
                )
            ] ),
        ),
        instances: [
            new NIInstance(
                name: 'i-00000001',
                ownerId: '000000000002',
                macAddress: '00:00:00:00:00:00',
                publicIp: '2.0.0.0',
                privateIp: '10.0.0.0',
                securityGroups: [],
            )
        ],
        securityGroups: [ ]
    ), info )
  }

  private static Cluster cluster( String partition, String host, List<String> nodes = [ ] ) {
    Cluster cluster = new Cluster( new ClusterConfiguration( partition: partition, hostName: host ), (Void) null ){ }
    nodes.each{ String node -> cluster.nodeMap.put( node, new NodeInfo( name: node ) ) }
    cluster
  }

  private static NetworkInfoBroadcaster.VmInstanceNetworkView instance( String id, String partition, String node, String ownerAccountNumber, String mac, String publicAddress, String privateAddress ) {
    new NetworkInfoBroadcaster.VmInstanceNetworkView(
      id,
      VmState.RUNNING,
      false,
      ownerAccountNumber,
      null,
      null,
      mac,
      privateAddress,
      publicAddress,
      partition,
      node,
      [ ],
    )
  }

  private static NetworkInfoBroadcaster.NetworkGroupNetworkView group(
      String id,
      String ownerAccountNumber,
      List<String> rules,
      List<NetworkInfoBroadcaster.IPPermissionNetworkView> ingressRules,
      List<NetworkInfoBroadcaster.IPPermissionNetworkView> egressRules
  ) {
    new NetworkInfoBroadcaster.NetworkGroupNetworkView(
      id,
      ownerAccountNumber,
      rules,
      ingressRules,
      egressRules
    )
  }
}
