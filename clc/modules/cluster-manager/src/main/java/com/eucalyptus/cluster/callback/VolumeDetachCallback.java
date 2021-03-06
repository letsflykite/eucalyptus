/*************************************************************************
 * Copyright 2009-2012 Eucalyptus Systems, Inc.
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
 *
 * This file may incorporate work covered under the following copyright
 * and permission notice:
 *
 *   Software License Agreement (BSD License)
 *
 *   Copyright (c) 2008, Regents of the University of California
 *   All rights reserved.
 *
 *   Redistribution and use of this software in source and binary forms,
 *   with or without modification, are permitted provided that the
 *   following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *   COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *   BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *   LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *   ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE. USERS OF THIS SOFTWARE ACKNOWLEDGE
 *   THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE LICENSED MATERIAL,
 *   COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS SOFTWARE,
 *   AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *   IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA,
 *   SANTA BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY,
 *   WHICH IN THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION,
 *   REPLACEMENT OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO
 *   IDENTIFIED, OR WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT
 *   NEEDED TO COMPLY WITH ANY SUCH LICENSES OR RIGHTS.
 ************************************************************************/

package com.eucalyptus.cluster.callback;

import org.apache.log4j.Logger;
import com.eucalyptus.entities.Entities;
import com.eucalyptus.util.Exceptions;
import com.eucalyptus.util.async.ConnectionException;
import com.eucalyptus.util.async.FailedRequestException;
import com.eucalyptus.util.async.MessageCallback;
import com.eucalyptus.compute.common.internal.vm.VmInstance;
import com.eucalyptus.vm.VmInstances;
import com.eucalyptus.compute.common.internal.vm.VmVolumeAttachment;
import com.eucalyptus.compute.common.internal.vm.VmVolumeAttachment.AttachmentState;
import com.google.common.base.Function;
import edu.ucsb.eucalyptus.msgs.ClusterDetachVolumeResponseType;
import edu.ucsb.eucalyptus.msgs.ClusterDetachVolumeType;

public class VolumeDetachCallback extends MessageCallback<ClusterDetachVolumeType,ClusterDetachVolumeResponseType> {
  
  private static Logger LOG = Logger.getLogger( VolumeDetachCallback.class );
  
  public VolumeDetachCallback( ClusterDetachVolumeType request ) {
    super( request );
    final Function<String, VmInstance> removeVolAttachment = new Function<String, VmInstance>( ) {
      public VmInstance apply( final String input ) {
        String volumeId = VolumeDetachCallback.this.getRequest( ).getVolumeId( );
        VmInstance vm = VmInstances.lookup( input );
        VmVolumeAttachment volumeAttachment = vm.lookupVolumeAttachment( volumeId );
        if ( !VmVolumeAttachment.AttachmentState.attached.equals( volumeAttachment.getAttachmentState( ) ) ) {
          if ( VmVolumeAttachment.AttachmentState.detaching.equals( volumeAttachment.getAttachmentState( ) ) ) {
            throw Exceptions.toUndeclared( "Failed to detach volume which is already detaching: " + volumeId );
          } else if ( VmVolumeAttachment.AttachmentState.attaching.equals( volumeAttachment.getAttachmentState( ) ) ) {
            throw Exceptions.toUndeclared( "Failed to detach volume which is currently attaching: " + volumeId );
          } else {
            throw Exceptions.toUndeclared( "Failed to detach volume which is not currently attached: " + volumeId );
          }
        }
        VmInstances.updateVolumeAttachment( vm, volumeId, AttachmentState.detaching );
        return vm;
      }
    };
    Entities.asTransaction( VmInstance.class, removeVolAttachment ).apply( this.getRequest( ).getInstanceId( ) );
  }
  
  @Override
  public void fire( ClusterDetachVolumeResponseType reply ) {}

  /**
   * TODO: DOCUMENT
   * @see com.eucalyptus.util.async.MessageCallback#fireException(java.lang.Throwable)
   * @param e
   */
  @Override
  public void fireException( Throwable e ) {
    if( e instanceof FailedRequestException ) {
      LOG.debug( "Request failed: " + this.getRequest( ).toSimpleString( ) + " because of: " + e.getMessage( ) );
    } else if( e instanceof ConnectionException ) {
      LOG.error( e, e );
    }
    LOG.trace( this.getRequest( ).toString( "eucalyptus_ucsb_edu" ) );
    final Function<String, VmInstance> failedVolDetach = new Function<String, VmInstance>( ) {
      public VmInstance apply( final String input ) {
        VmInstance vm = VmInstances.lookup( input );
        VmInstances.updateVolumeAttachment( vm, VolumeDetachCallback.this.getRequest( ).getVolumeId( ), AttachmentState.attached );
        return vm;
      }
    };
    Entities.asTransaction( VmInstance.class, failedVolDetach ).apply( this.getRequest( ).getInstanceId( ) );
  }
  
}
