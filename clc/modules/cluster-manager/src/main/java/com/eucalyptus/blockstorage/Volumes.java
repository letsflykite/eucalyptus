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

package com.eucalyptus.blockstorage;

import static com.eucalyptus.reporting.event.VolumeEvent.VolumeAction;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.Date;

import javax.persistence.EntityTransaction;

import org.apache.log4j.Logger;
import org.hibernate.criterion.Example;

import com.eucalyptus.auth.principal.UserFullName;
import com.eucalyptus.blockstorage.msgs.CreateStorageVolumeResponseType;
import com.eucalyptus.blockstorage.msgs.CreateStorageVolumeType;
import com.eucalyptus.blockstorage.msgs.DescribeStorageVolumesResponseType;
import com.eucalyptus.blockstorage.msgs.DescribeStorageVolumesType;
import com.eucalyptus.compute.common.CloudMetadata.VolumeMetadata;
import com.eucalyptus.component.Partitions;
import com.eucalyptus.component.ServiceConfiguration;
import com.eucalyptus.component.Topology;
import com.eucalyptus.compute.common.internal.blockstorage.State;
import com.eucalyptus.compute.common.internal.blockstorage.Volume;
import com.eucalyptus.compute.common.internal.blockstorage.VolumeTag;
import com.eucalyptus.compute.common.internal.identifier.ResourceIdentifiers;
import com.eucalyptus.entities.Entities;
import com.eucalyptus.entities.TransactionResource;
import com.eucalyptus.entities.Transactions;
import com.eucalyptus.event.ListenerRegistry;
import com.eucalyptus.records.Logs;
import com.eucalyptus.reporting.event.EventActionInfo;
import com.eucalyptus.reporting.event.VolumeEvent;
import com.eucalyptus.util.Callback;
import com.eucalyptus.util.EucalyptusCloudException;
import com.eucalyptus.util.Exceptions;
import com.eucalyptus.util.OwnerFullName;
import com.eucalyptus.util.RestrictedTypes.QuantityMetricFunction;
import com.eucalyptus.util.RestrictedTypes.UsageMetricFunction;
import com.eucalyptus.util.async.AsyncRequests;
import com.eucalyptus.vm.VmInstances;
import com.eucalyptus.compute.common.internal.vm.VmVolumeAttachment;
import com.eucalyptus.compute.common.internal.tags.FilterSupport;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

import edu.ucsb.eucalyptus.cloud.VolumeSizeExceededException;
import edu.ucsb.eucalyptus.msgs.BaseMessage;

public class Volumes {
  private static Logger     LOG                   = Logger.getLogger( Volumes.class );

  @QuantityMetricFunction( VolumeMetadata.class )
  public enum CountVolumes implements Function<OwnerFullName, Long> {
    INSTANCE;

    @Override
    public Long apply( final OwnerFullName input ) {
      final EntityTransaction db = Entities.get( Volume.class );
      try {
        return Entities.count( Volume.named( input, null ) );
      } finally {
        db.rollback( );
      }
    }
  }

  @UsageMetricFunction( VolumeMetadata.class )
  public enum MeasureVolumes implements Function<OwnerFullName, Long> {
    INSTANCE;

    @SuppressWarnings( "unchecked" )
    @Override
    public Long apply( final OwnerFullName input ) {
      Long size = 0l;
      final EntityTransaction db = Entities.get( Volume.class );
      try {
        final List<Volume> vols = Entities.createCriteria( Volume.class )
                                          .add( Example.create( Volume.named( input, null ) ) )
                                          .setReadOnly( true )
                                          .setCacheable( false )
                                          .list( );
        for ( final Volume v : vols ) {
          size += v.getSize( );
        }
      } finally {
        db.rollback( );
      }
      return size;
    }

  }

  public static Volume checkVolumeReady( final Volume vol ) throws EucalyptusCloudException {
    if ( vol.isReady( ) ) {
      return vol;
    } else {
      //TODO:GRZE:REMOVE temporary workaround to update the volume state.
      final ServiceConfiguration sc = Topology.lookup( Storage.class, Partitions.lookupByName( vol.getPartition( ) ) );
      final DescribeStorageVolumesType descVols = new DescribeStorageVolumesType( Lists.newArrayList( vol.getDisplayName( ) ) );
      try {
        Transactions.one( Volume.named( null, vol.getDisplayName( ) ), new Callback<Volume>( ) {
          
          @Override
          public void fire( final Volume t ) {
            try {
              final DescribeStorageVolumesResponseType volState = AsyncRequests.sendSync( sc, descVols );
              if ( !volState.getVolumeSet( ).isEmpty( ) ) {
                final State newVolumeState = Volumes.transformStorageState( vol.getState( ), volState.getVolumeSet( ).get( 0 ).getStatus( ) );
                vol.setState( newVolumeState );
              }
            } catch ( final Exception ex ) {
              LOG.error( ex );
              Logs.extreme( ).error( ex, ex );
              throw Exceptions.toUndeclared( "Failed to update the volume state " + vol.getDisplayName( ) + " not yet ready", ex );
            }
          }
        } );
      } catch ( final ExecutionException ex ) {
        throw new EucalyptusCloudException( ex.getCause( ) );
      }
      if ( !vol.isReady( ) ) {
        throw new EucalyptusCloudException( "Volume " + vol.getDisplayName( ) + " not yet ready" );
      }
      return vol;
    }
  }
  
  public static Volume lookup( final OwnerFullName ownerFullName, final String volumeId ) {
    try ( final TransactionResource db =
        Entities.transactionFor( Volume.class ) ){
      try{
        Volume volume = Entities.uniqueResult( Volume.named( ownerFullName, volumeId ) );
        db.commit( );
        return volume;
      } catch ( final NoSuchElementException e ) {
        throw e;
      } catch ( final Exception ex ) {
        LOG.debug( ex, ex );
        throw Exceptions.toUndeclared( ex );
      }
    }
  }
  
  public static Volume createStorageVolume( final ServiceConfiguration sc, final UserFullName owner, final String snapId, final Integer newSize, final BaseMessage request ) throws ExecutionException {
    final String newId = ResourceIdentifiers.generateString( Volume.ID_PREFIX );
    LOG.debug("Creating volume");
    final Volume newVol = Transactions.save( Volume.create( sc, owner, snapId, newSize, newId ), new Callback<Volume>( ) {
      
      @Override
      public void fire( final Volume t ) {
        t.setState( State.GENERATING );
        try {
          final CreateStorageVolumeType req = new CreateStorageVolumeType( t.getDisplayName( ), t.getSize( ), snapId, null );
          final CreateStorageVolumeResponseType ret = AsyncRequests.sendSync( sc, req );
          LOG.debug("Volume created");

          if ( t.getSize() != null && t.getSize() > 0 ) {
            fireUsageEvent( t, VolumeEvent.forVolumeCreate());
          }
        } catch ( final Exception ex ) {
          final VolumeSizeExceededException volumeSizeException =
              Exceptions.findCause( ex, VolumeSizeExceededException.class );
          if ( volumeSizeException != null ) {
            LOG.debug( "Failed to create volume: " + t.getDisplayName() + " due to " + volumeSizeException.getLocalizedMessage() );
          } else {
            LOG.error( "Failed to create volume: " + t.getDisplayName(), ex );
          }
          t.setState( State.FAIL );
          throw Exceptions.toUndeclared( ex );
        }
      }
      
    } );
    return newVol;
  }
  
  public static void annihilateStorageVolume( Volume volume) {
    volume.setState( State.ANNIHILATING );
    fireUsageEvent( volume, VolumeEvent.forVolumeDelete());
  }

  static State transformStorageState( final State volumeState, final String storageState ) {
    if ( State.GENERATING.equals( volumeState ) ) {
      if ( "failed".equals( storageState ) ) {
        return State.FAIL;
      } else if ( "error".equals( storageState ) ) {
    	return State.ERROR;
      } else if ("available".equals(storageState) ) {
        return State.EXTANT;
      } else {
        return State.GENERATING;
      }
    } else if ( State.ANNIHILATING.equals( volumeState ) ) {
      if ("deleted".equals(storageState) ) {
        return State.ANNIHILATED;
      } else {
        return State.ANNIHILATING;
      }
    } else if ( !State.ANNIHILATING.equals( volumeState ) && !State.BUSY.equals( volumeState ) ) {
      if ( "failed".equals(storageState) ) {
        return State.FAIL;
      } else if ( "creating".equals(storageState) ) {
        return State.GENERATING;
      } else if ( "available".equals(storageState) ) {
        return State.EXTANT;
      } else if ( "in-use".equals( storageState ) ) {
        return State.BUSY;
      } else if ( "error".equals( storageState ) ) {
    	return State.ERROR;
      } else {
        return State.ANNIHILATED;
      }
    } else if ( State.BUSY.equals( volumeState ) ) {
      return State.BUSY;
    } else if ( State.ERROR.equals( volumeState ) ) {
      if ( "available".equals(storageState) ) {
        return State.EXTANT;
      } else if ( "deleted".equals(storageState) ) {
        return State.ANNIHILATED;
      } else {
    	return State.ERROR; 
      }
    } else {
      if ("failed".equals(storageState) ) {
        return State.FAIL;
      } else if( "error".equals(storageState) ) {
    	return State.ERROR;  
      } else {
        return State.ANNIHILATED;
      }
    }
  }

  static void fireUsageEvent( final Volume volume,
                              final EventActionInfo<VolumeAction> actionInfo ) {
    try {
      ListenerRegistry.getInstance().fireEvent(
          VolumeEvent.with(
              actionInfo,
              volume.getNaturalId(),
              volume.getDisplayName(),
              volume.getSize(),
              volume.getOwner(),
              volume.getPartition())
      );
    } catch (final Throwable e) {
      LOG.error("Error creating/inserting reporting event " + (actionInfo == null ? "null" : actionInfo.getAction().toString()) + " for volume " + (volume == null ? "null" : volume.getDisplayName()), e);
    }
  }
  
  public static class VolumeFilterSupport extends FilterSupport<Volume>{
	  public VolumeFilterSupport(){
		  super( builderFor(Volume.class)
				  .withTagFiltering( VolumeTag.class, "volume" )
				  .withDateProperty("attachment.attach-time", FilterDateFunctions.ATTACHMENT_ATTACH_TIME)
				  .withBooleanProperty("attachment.delete-on-termination", FilterBooleanFunctions.ATTACHMENT_DELETE_ON_TERMINATION)
				  .withStringProperty("attachment.device", FilterStringFunctions.ATTACHMENT_DEVICE)
				  .withStringProperty("attachment.instance-id", FilterStringFunctions.ATTACHMENT_INSTANCE_ID)
				  .withStringProperty("attachment.status", FilterStringFunctions.ATTACHMENT_STATUS)
				  .withStringProperty("availability-zone", FilterStringFunctions.AVAILABILITY_ZONE)
				  .withDateProperty("create-time", FilterDateFunctions.CREATE_TIME)
				  .withStringProperty("size", FilterStringFunctions.SIZE)
				  .withStringProperty("snapshot-id", FilterStringFunctions.SNAPSHOT_ID)
				  .withStringProperty("status", FilterStringFunctions.STATUS)
				  .withStringProperty("volume-id", FilterStringFunctions.VOLUME_ID)
				  .withConstantProperty("volume-type", "standard")
				  .withPersistenceFilter( "availability-zone", "partition" )
				  .withPersistenceFilter( "create-time", "creationTimestamp", PersistenceFilter.Type.Date )
				  .withPersistenceFilter( "size", "size", PersistenceFilter.Type.Integer )
				  .withPersistenceFilter( "snapshot-id", "parentSnapshot" )
				  .withPersistenceFilter( "volume-id", "displayName" ) );
	  }
  }

  private enum FilterStringFunctions implements Function<Volume,String> {
	  ATTACHMENT_DEVICE {
		  @Override
		  public String apply(final Volume vol){
				try{
					VmVolumeAttachment attachment = VmInstances.lookupVolumeAttachment( vol.getDisplayName( ) );
					return attachment.getDevice( );
				}catch (final Throwable e) {
					return null;
				}
		  }
	  },
	  ATTACHMENT_INSTANCE_ID {
		  @Override
		  public String apply(final Volume vol){
			  try{
				  VmVolumeAttachment attachment = VmInstances.lookupVolumeAttachment( vol.getDisplayName() );
				  return attachment.getVmInstance().getInstanceId();
			  }catch (final Throwable e) {
				  return null;
			  }
		  }
	  },
	  ATTACHMENT_STATUS {
		  @Override
		  public String apply(final Volume vol){
			 try{
				 VmVolumeAttachment attachment = VmInstances.lookupVolumeAttachment(vol.getDisplayName());
				 return attachment.getStatus();
			 }catch (final Throwable e){
				 return null;
			 }
		  }
	  },
	  AVAILABILITY_ZONE {
		  @Override
		  public String apply(final Volume vol){
			  return vol.getPartition();
		  }
	  },
	  SIZE {
		  @Override
		  public String apply(final Volume vol){
			  return vol.getSize().toString();
		  }
	  },
	  SNAPSHOT_ID {
		  @Override
		  public String apply(final Volume vol){
			  return vol.getParentSnapshot();
		  }
	  },
	  STATUS {
		  @Override
		  public String apply(final Volume vol){
			return vol.mapState();
		  }
	  },
	  VOLUME_ID {
		  @Override
		  public String apply (final Volume vol){
			  return vol.getDisplayName();
		  }
	  },
  }

  private enum FilterDateFunctions implements Function<Volume, Date>{
	  ATTACHMENT_ATTACH_TIME {
		  @Override
		  public Date apply(final Volume vol){
			  try{
				  VmVolumeAttachment attachment = VmInstances.lookupVolumeAttachment(vol.getDisplayName());
				  return attachment.getAttachTime();
			  }catch(final Throwable e){
				  return null;
			  }
		  }
	  },
	  CREATE_TIME {
		  @Override
		  public Date apply(final Volume vol){
			  return vol.getCreationTimestamp();
		  }
	  }
  }
  private enum FilterBooleanFunctions implements Function<Volume, Boolean> {
	  ATTACHMENT_DELETE_ON_TERMINATION {
		@Override
		public Boolean apply(final Volume vol){
			 try{
				 VmVolumeAttachment attachment = VmInstances.lookupVolumeAttachment(vol.getDisplayName());
				 return attachment.getDeleteOnTerminate();
			 }catch (final Throwable e){
				 return false;
			 }
		}
	  }
  }
}
