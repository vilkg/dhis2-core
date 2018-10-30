package org.hisp.dhis.eventdatavalue;/*
 * Copyright (c) 2004-2018, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import com.google.common.collect.Sets;
import org.hisp.dhis.common.AuditType;
import org.hisp.dhis.common.IllegalQueryException;
import org.hisp.dhis.dataelement.DataElement;
import org.hisp.dhis.dataelement.DataElementService;
import org.hisp.dhis.fileresource.FileResource;
import org.hisp.dhis.fileresource.FileResourceService;
import org.hisp.dhis.program.ProgramStageInstance;
import org.hisp.dhis.program.ProgramStageInstanceService;
import org.hisp.dhis.system.util.ValidationUtils;
import org.hisp.dhis.trackedentitydatavalue.TrackedEntityDataValueAudit;
import org.hisp.dhis.trackedentitydatavalue.TrackedEntityDataValueAuditService;
import org.hisp.dhis.user.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author David Katuscak
 */
public class DefaultEventDataValueService implements EventDataValueService
{
    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    @Autowired
    private TrackedEntityDataValueAuditService dataValueAuditService;

    @Autowired
    private FileResourceService fileResourceService;

    @Autowired
    private ProgramStageInstanceService programStageInstanceService;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private DataElementService dataElementService;

    @Override
    public void persistDataValues( Set<EventDataValue> newDataValues, Set<EventDataValue> updatedDataValues,
        Set<EventDataValue> removedDataValues, Map<String, DataElement> dataElementsCache, ProgramStageInstance programStageInstance,
        boolean singleValue ) {

        Set<EventDataValue> updatedOrNewDataValues = Sets.union( newDataValues, updatedDataValues );

        if ( singleValue ) {
            //If it is only a single value update, I don't won't to miss the values that are missing in the payload but already present in the DB
            Set<EventDataValue> changedDataValues = Sets.union( updatedOrNewDataValues, removedDataValues );
            Set<EventDataValue> unchangedDataValues = Sets.difference( programStageInstance.getEventDataValues(), changedDataValues );
            programStageInstance.setEventDataValues( updatedOrNewDataValues );
            programStageInstance.getEventDataValues().addAll( unchangedDataValues );
        }
        else {
            programStageInstance.setEventDataValues( updatedOrNewDataValues );
        }

        auditDataValuesChanges( newDataValues, updatedDataValues, removedDataValues, dataElementsCache, programStageInstance );
        handleFileDataValueChanges( newDataValues, updatedDataValues, removedDataValues, dataElementsCache );
        programStageInstanceService.updateProgramStageInstance( programStageInstance );
    }


    @Override
    public void saveEventDataValue( ProgramStageInstance programStageInstance, EventDataValue eventDataValue )
    {

        if ( !StringUtils.isEmpty( eventDataValue.getValue() ) )
        {
            if ( StringUtils.isEmpty( eventDataValue.getStoredBy() ) )
            {
                eventDataValue.setStoredBy( currentUserService.getCurrentUsername() );
            }

            if ( StringUtils.isEmpty( eventDataValue.getDataElement() ) )
            {
                throw new IllegalQueryException( "Data element is null or empty" );
            }
            DataElement dataElement = dataElementService.getDataElement( eventDataValue.getDataElement() );

            if ( dataElement == null ) {
                throw new IllegalStateException( "Given data element (" +  eventDataValue.getDataElement() + ") does not exist" );
            }

            String result = ValidationUtils.dataValueIsValid( eventDataValue.getValue(), dataElement.getValueType() );

            if ( result != null )
            {
                throw new IllegalQueryException( "Value is not valid:  " + result );
            }

            if ( dataElement.isFileType() )
            {
                handleFileDataValueSave( eventDataValue, dataElement );
            }

            programStageInstance.getEventDataValues().add( eventDataValue );
            createAndAddAudit( eventDataValue, dataElement, programStageInstance, AuditType.CREATE );
            programStageInstanceService.updateProgramStageInstance( programStageInstance );
        }
    }

    @Override public void saveEventDataValues( ProgramStageInstance programStageInstance, Collection<EventDataValue> eventDataValues )
    {

    }

    @Override public void updateEventDataValue( ProgramStageInstance programStageInstance, EventDataValue eventDataValue )
    {

    }

    @Override public void updateEventDataValues( ProgramStageInstance programStageInstance, Collection<EventDataValue> eventDataValues )
    {

    }

    @Override public void deleteEventDataValue( ProgramStageInstance programStageInstance, EventDataValue eventDataValue )
    {

    }

    @Override public void deleteEventDataValues( ProgramStageInstance programStageInstance, Collection<EventDataValue> eventDataValues )
    {

    }

    @Override public void deleteEventDataValue( ProgramStageInstance programStageInstance )
    {

    }

    // -------------------------------------------------------------------------
    // Implementation methods
    // -------------------------------------------------------------------------

//      // Safe to remove - all usages replaced - audit logging need to be finished. Not completely done yet (e.g. PdfDataEntryFormImportUtil)
//      // Also the file handling need to implemented/fixed/checked.
//    @Override
//    public void saveTrackedEntityDataValue( TrackedEntityDataValue trackedEntityDataValue )
//    {
//        trackedEntityDataValue.setAutoFields();
//
//        if ( !StringUtils.isEmpty( trackedEntityDataValue.getValue() ) )
//        {
//            if ( StringUtils.isEmpty( trackedEntityDataValue.getStoredBy() ) )
//            {
//                trackedEntityDataValue.setStoredBy( currentUserService.getCurrentUsername() );
//            }
//
//            if ( trackedEntityDataValue.getDataElement() == null || trackedEntityDataValue.getDataElement().getValueType() == null )
//            {
//                throw new IllegalQueryException( "Data element or type is null or empty" );
//            }
//
//            String result = dataValueIsValid( trackedEntityDataValue.getValue(), trackedEntityDataValue.getDataElement().getValueType() );
//
//            if ( result != null )
//            {
//                throw new IllegalQueryException( "Value is not valid:  " + result );
//            }
//
//            if ( trackedEntityDataValue.getDataElement().isFileType() )
//            {
//                handleFileDataValueSave( trackedEntityDataValue );
//            }
//
//            dataValueStore.saveVoid( trackedEntityDataValue );
//        }
//    }

//      // Safe to remove - all usages replaced - audit logging need to be finished. Not completely done yet (e.g. PdfDataEntryFormImportUtil)
//      // Also the file handling need to implemented/fixed/checked.
//    @Override
//    public void updateTrackedEntityDataValue( TrackedEntityDataValue trackedEntityDataValue )
//    {
//        trackedEntityDataValue.setAutoFields();
//
//        if ( StringUtils.isEmpty( trackedEntityDataValue.getValue() ) )
//        {
//            deleteTrackedEntityDataValue( trackedEntityDataValue );
//        }
//        else
//        {
//            if ( StringUtils.isEmpty( trackedEntityDataValue.getStoredBy() ) )
//            {
//                trackedEntityDataValue.setStoredBy( currentUserService.getCurrentUsername() );
//            }
//
//            if ( trackedEntityDataValue.getDataElement() == null || trackedEntityDataValue.getDataElement().getValueType() == null )
//            {
//                throw new IllegalQueryException( "Data element or type is null or empty" );
//            }
//
//            String result = dataValueIsValid( trackedEntityDataValue.getValue(), trackedEntityDataValue.getDataElement().getValueType() );
//
//            if ( result != null )
//            {
//                throw new IllegalQueryException( "Value is not valid:  " + result );
//            }
//
//            createAndAddAudit( trackedEntityDataValue, trackedEntityDataValue.getStoredBy(), AuditType.UPDATE );
//            handleFileDataValueUpdate( trackedEntityDataValue );
//
//            dataValueStore.update( trackedEntityDataValue );
//        }
//    }

//      // Safe to remove - all usages replaced - the file handling needs to be implemented/fixed/checked
//    @Override
//    public void deleteTrackedEntityDataValue( TrackedEntityDataValue dataValue )
//    {
//        createAndAddAudit( dataValue, currentUserService.getCurrentUsername(), AuditType.DELETE );
//
//        handleFileDataValueDelete( dataValue );
//
//        dataValueStore.delete( dataValue );
//    }

//      // Safe to remove - no usage at all
//    @Override
//    public void deleteTrackedEntityDataValue( ProgramStageInstance programStageInstance )
//    {
//        List<TrackedEntityDataValue> dataValues = dataValueStore.get( programStageInstance );
//        String username = currentUserService.getCurrentUsername();
//
//        for ( TrackedEntityDataValue dataValue : dataValues )
//        {
//            createAndAddAudit( dataValue, username, AuditType.DELETE );
//            handleFileDataValueDelete( dataValue );
//        }
//
//        dataValueStore.delete( programStageInstance );
//    }

//      // Safe to remove - test/s should be rewritten
//    @Override
//    public List<TrackedEntityDataValue> getTrackedEntityDataValues( ProgramStageInstance programStageInstance )
//    {
//        return dataValueStore.get( programStageInstance );
//    }

//      // Safe to remove - logic is re-implemented, so safe to take it away
//    @Override
//    public List<TrackedEntityDataValue> getTrackedEntityDataValuesForSynchronization( ProgramStageInstance programStageInstance )
//    {
//        return dataValueStore.getTrackedEntityDataValuesForSynchronization( programStageInstance );
//    }

//      // Safe to remove - not used at all - only in tests so doesn't make sense
//    @Override
//    public List<TrackedEntityDataValue> getTrackedEntityDataValues( ProgramStageInstance programStageInstance,
//        Collection<DataElement> dataElements )
//    {
//        return dataValueStore.get( programStageInstance, dataElements );
//    }

//      // Safe to remove - not used at all - only in tests so doesn't make sense
//    @Override
//    public List<TrackedEntityDataValue> getTrackedEntityDataValues( Collection<ProgramStageInstance> programStageInstances )
//    {
//        return dataValueStore.get( programStageInstances );
//    }

//      // Safe to remove - not used at all - only in tests so doesn't make sense
//    @Override
//    public List<TrackedEntityDataValue> getTrackedEntityDataValues( DataElement dataElement )
//    {
//        return dataValueStore.get( dataElement );
//    }

//      // Safe to remove - not used at all - only in 1 test and even there in wrong way
//    @Override
//    public List<TrackedEntityDataValue> getTrackedEntityDataValues( TrackedEntityInstance entityInstance,
//        Collection<DataElement> dataElements, Date startDate, Date endDate )
//    {
//        return dataValueStore.get( entityInstance, dataElements, startDate, endDate );
//    }

//      // Safe to remove - maybe tests can be rewritten
//    @Override
//    public TrackedEntityDataValue getTrackedEntityDataValue( ProgramStageInstance programStageInstance,
//        DataElement dataElement )
//    {
//        return dataValueStore.get( programStageInstance, dataElement );
//    }

    // -------------------------------------------------------------------------
    // Supportive methods
    // -------------------------------------------------------------------------

    private void auditDataValuesChanges( Set<EventDataValue> newDataValues, Set<EventDataValue> updatedDataValues,
        Set<EventDataValue> removedDataValues, Map<String, DataElement> dataElementsCache, ProgramStageInstance programStageInstance ) {

        newDataValues.forEach( dv -> createAndAddAudit( dv, dataElementsCache.get( dv.getDataElement() ), programStageInstance, AuditType.CREATE ) );
        updatedDataValues.forEach( dv -> createAndAddAudit( dv, dataElementsCache.get( dv.getDataElement() ), programStageInstance, AuditType.UPDATE ) );
        removedDataValues.forEach( dv -> createAndAddAudit( dv, dataElementsCache.get( dv.getDataElement() ), programStageInstance, AuditType.DELETE ) );
    }

    private void createAndAddAudit( EventDataValue dataValue, DataElement dataElement, ProgramStageInstance programStageInstance,
        AuditType auditType )
    {
        TrackedEntityDataValueAudit dataValueAudit = new TrackedEntityDataValueAudit( dataElement, programStageInstance,
            dataValue.getValue(), dataValue.getStoredBy(), dataValue.getProvidedElsewhere(), auditType );
        dataValueAuditService.addTrackedEntityDataValueAudit( dataValueAudit );
    }

    private void handleFileDataValueChanges ( Set<EventDataValue> newDataValues, Set<EventDataValue> updatedDataValues,
        Set<EventDataValue> removedDataValues, Map<String, DataElement> dataElementsCache ) {

        removedDataValues.forEach( dv -> handleFileDataValueDelete( dv, dataElementsCache.get( dv.getDataElement() ) ) );
        updatedDataValues.forEach( dv -> handleFileDataValueUpdate( dv, dataElementsCache.get( dv.getDataElement() ) ) );
        newDataValues.forEach( dv -> handleFileDataValueSave( dv, dataElementsCache.get( dv.getDataElement() ) ) );
    }

    private void handleFileDataValueUpdate( EventDataValue dataValue, DataElement dataElement )
    {
        String previousFileResourceUid = dataValue.getAuditValue();

        if ( previousFileResourceUid == null || previousFileResourceUid.equals( dataValue.getValue() ) )
        {
            return;
        }

        FileResource fileResource = fetchFileResource( dataValue, dataElement );

        if ( fileResource == null )
        {
            return;
        }

        fileResourceService.deleteFileResource( previousFileResourceUid );

        setAssigned( fileResource );
    }

    /**
     * Update FileResource with 'assigned' status.
     */
    private void handleFileDataValueSave( EventDataValue dataValue, DataElement dataElement )
    {
        FileResource fileResource = fetchFileResource( dataValue, dataElement );

        if ( fileResource == null )
        {
            return;
        }

        setAssigned( fileResource );
    }

    /**
     * Delete associated FileResource if it exists.
     */
    private void handleFileDataValueDelete( EventDataValue dataValue, DataElement dataElement )
    {
        FileResource fileResource = fetchFileResource( dataValue, dataElement );

        if ( fileResource == null )
        {
            return;
        }

        fileResourceService.deleteFileResource( fileResource.getUid() );
    }

    private FileResource fetchFileResource( EventDataValue dataValue, DataElement dataElement )
    {
        if ( !dataElement.isFileType() )
        {
            return null;
        }

        return fileResourceService.getFileResource( dataValue.getValue() );
    }

    private void setAssigned( FileResource fileResource )
    {
        fileResource.setAssigned( true );
        fileResourceService.updateFileResource( fileResource );
    }
}
