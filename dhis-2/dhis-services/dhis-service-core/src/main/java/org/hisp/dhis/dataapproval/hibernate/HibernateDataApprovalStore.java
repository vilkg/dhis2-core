package org.hisp.dhis.dataapproval.hibernate;

/*
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

import static org.hisp.dhis.dataapproval.DataApprovalState.ACCEPTED_HERE;
import static org.hisp.dhis.dataapproval.DataApprovalState.APPROVED_ABOVE;
import static org.hisp.dhis.dataapproval.DataApprovalState.APPROVED_HERE;
import static org.hisp.dhis.dataapproval.DataApprovalState.UNAPPROVABLE;
import static org.hisp.dhis.dataapproval.DataApprovalState.UNAPPROVED_ABOVE;
import static org.hisp.dhis.dataapproval.DataApprovalState.UNAPPROVED_READY;
import static org.hisp.dhis.dataapproval.DataApprovalState.UNAPPROVED_WAITING;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.persistence.criteria.CriteriaBuilder;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hisp.dhis.api.util.DateUtils;
import org.hisp.dhis.cache.Cache;
import org.hisp.dhis.cache.CacheProvider;
import org.hisp.dhis.category.CategoryCombo;
import org.hisp.dhis.category.CategoryOptionCombo;
import org.hisp.dhis.category.CategoryService;
import org.hisp.dhis.common.IdentifiableObjectUtils;
import org.hisp.dhis.commons.util.SystemUtils;
import org.hisp.dhis.dataapproval.DataApproval;
import org.hisp.dhis.dataapproval.DataApprovalLevel;
import org.hisp.dhis.dataapproval.DataApprovalLevelService;
import org.hisp.dhis.dataapproval.DataApprovalState;
import org.hisp.dhis.dataapproval.DataApprovalStatus;
import org.hisp.dhis.dataapproval.DataApprovalStore;
import org.hisp.dhis.dataapproval.DataApprovalWorkflow;
import org.hisp.dhis.hibernate.HibernateGenericStore;
import org.hisp.dhis.jdbc.StatementBuilder;
import org.hisp.dhis.organisationunit.OrganisationUnit;
import org.hisp.dhis.period.Period;
import org.hisp.dhis.period.PeriodService;
import org.hisp.dhis.setting.SettingKey;
import org.hisp.dhis.setting.SystemSettingManager;
import org.hisp.dhis.user.CurrentUserService;
import org.hisp.dhis.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.support.rowset.SqlRowSet;

/**
 * @author Jim Grace
 */
public class HibernateDataApprovalStore
    extends HibernateGenericStore<DataApproval>
    implements DataApprovalStore
{
    private static final Log log = LogFactory.getLog( HibernateDataApprovalStore.class );

    private static final int MAX_APPROVAL_LEVEL = 100000000;

    private static final String SQL_CONCAT = "-";
    private static final String SQL_CAT = StatementBuilder.QUOTE + SQL_CONCAT + StatementBuilder.QUOTE;

    private Cache<Boolean> IS_APPROVED_CACHE;

    @Autowired
    private CacheProvider cacheProvider;

    @Autowired
    private Environment env;

    @PostConstruct
    public void init()
    {
        IS_APPROVED_CACHE = cacheProvider.newCacheBuilder( Boolean.class )
            .forRegion( "isDataApproved" )
            .expireAfterAccess( 12, TimeUnit.HOURS )
            .withMaximumSize( SystemUtils.isTestRun(env.getActiveProfiles()) ? 0 : 20000 ).build();
    }

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private PeriodService periodService;

    public void setPeriodService( PeriodService periodService )
    {
        this.periodService = periodService;
    }

    private CurrentUserService currentUserService;

    public void setCurrentUserService( CurrentUserService currentUserService )
    {
        this.currentUserService = currentUserService;
    }

    private CategoryService categoryService;

    public void setCategoryService( CategoryService categoryService )
    {
        this.categoryService = categoryService;
    }

    private DataApprovalLevelService dataApprovalLevelService;

    public void setDataApprovalLevelService( DataApprovalLevelService dataApprovalLevelService )
    {
        this.dataApprovalLevelService = dataApprovalLevelService;
    }

    private SystemSettingManager systemSettingManager;

    public void setSystemSettingManager( SystemSettingManager systemSettingManager )
    {
        this.systemSettingManager = systemSettingManager;
    }

    @Autowired
    private StatementBuilder statementBuilder;

    // -------------------------------------------------------------------------
    // DataApproval
    // -------------------------------------------------------------------------

    @Override
    public void addDataApproval( DataApproval dataApproval )
    {
        IS_APPROVED_CACHE.invalidateAll();

        dataApproval.setPeriod( periodService.reloadPeriod( dataApproval.getPeriod() ) );

        save( dataApproval );
    }

    @Override
    public void updateDataApproval( DataApproval dataApproval )
    {
        IS_APPROVED_CACHE.invalidateAll();

        dataApproval.setPeriod( periodService.reloadPeriod( dataApproval.getPeriod() ) );

        update( dataApproval );
    }

    @Override
    public void deleteDataApproval( DataApproval dataApproval )
    {
        IS_APPROVED_CACHE.invalidateAll();

        dataApproval.setPeriod( periodService.reloadPeriod( dataApproval.getPeriod() ) );

        delete( dataApproval );
    }

    @Override
    public void deleteDataApprovals( OrganisationUnit organisationUnit )
    {
        IS_APPROVED_CACHE.invalidateAll();

        String hql = "delete from DataApproval d where d.organisationUnit = :unit";

        getSession().createQuery( hql ).
            setParameter( "unit", organisationUnit ).executeUpdate();
    }

    @Override
    public DataApproval getDataApproval( DataApproval dataApproval )
    {
        return getDataApproval( dataApproval.getDataApprovalLevel(), dataApproval.getWorkflow(),
            dataApproval.getPeriod(), dataApproval.getOrganisationUnit(), dataApproval.getAttributeOptionCombo() );
    }

    @Override
    public DataApproval getDataApproval( DataApprovalLevel dataApprovalLevel, DataApprovalWorkflow workflow, Period period,
        OrganisationUnit organisationUnit, CategoryOptionCombo attributeOptionCombo )
    {
        Period storedPeriod = periodService.reloadPeriod( period );

        CriteriaBuilder builder = getCriteriaBuilder();

        return getSingleResult( builder, newJpaParameters()
            .addPredicate( root -> builder.equal( root.get( "dataApprovalLevel" ), dataApprovalLevel ) )
            .addPredicate( root -> builder.equal( root.get( "workflow" ), workflow ) )
            .addPredicate( root -> builder.equal( root.get( "period" ), storedPeriod ) )
            .addPredicate( root -> builder.equal( root.get( "organisationUnit" ), organisationUnit ) )
            .addPredicate( root -> builder.equal( root.get( "attributeOptionCombo" ), attributeOptionCombo ) ) );
    }

    @Override
    public List<DataApproval> getDataApprovals( Collection<DataApprovalLevel> dataApprovalLevels, Collection<DataApprovalWorkflow> workflows,
        Collection<Period> periods, Collection<OrganisationUnit> organisationUnits, Collection<CategoryOptionCombo> attributeOptionCombos )
    {
        List<Period> storedPeriods = periods.stream().map(p -> periodService.reloadPeriod( p ) ).collect( Collectors.toList() );

        CriteriaBuilder builder = getCriteriaBuilder();

        return getList( builder, newJpaParameters()
            .addPredicate( root -> root.get( "dataApprovalLevel" ).in( dataApprovalLevels ) )
            .addPredicate( root -> root.get( "workflow" ).in( workflows ) )
            .addPredicate( root -> root.get( "period" ).in( storedPeriods ) )
            .addPredicate( root -> root.get( "organisationUnit" ).in( organisationUnits ) )
            .addPredicate( root -> root.get( "attributeOptionCombo" ).in( attributeOptionCombos ) ) );
    }

    @Override
    public boolean dataApprovalExists( DataApproval dataApproval )
    {
        return IS_APPROVED_CACHE.get( dataApproval.getCacheKey(), key -> dataApprovalExistsInternal( dataApproval ) ).orElse( false );
    }

    private boolean dataApprovalExistsInternal( DataApproval dataApproval )
    {
        Period storedPeriod = periodService.reloadPeriod( dataApproval.getPeriod() );

        String sql =
            "select dataapprovalid " +
            "from dataapproval " +
            "where dataapprovallevelid = " + dataApproval.getDataApprovalLevel().getId() + " " +
            "and workflowid = " + dataApproval.getWorkflow().getId() + " " +
            "and periodid  = " + storedPeriod.getId() + " " +
            "and organisationunitid = " + dataApproval.getOrganisationUnit().getId() + " " +
            "and attributeoptioncomboid = " + dataApproval.getAttributeOptionCombo().getId() + " " +
            "limit 1";

        return jdbcTemplate.queryForList( sql ).size() > 0;
    }

    @Override
    public List<DataApprovalStatus> getDataApprovalStatuses( DataApprovalWorkflow workflow,
        Period period, Collection<OrganisationUnit> orgUnits, int orgUnitLevel,
        CategoryCombo attributeCombo,
        Set<CategoryOptionCombo> attributeOptionCombos )
    {
        // ---------------------------------------------------------------------
        // Get validation criteria
        // ---------------------------------------------------------------------

        final User user = currentUserService.getCurrentUser();

        List<DataApprovalLevel> approvalLevels = workflow.getSortedLevels();

        List<DataApprovalLevel> userApprovalLevels = dataApprovalLevelService.getUserDataApprovalLevelsOrLowestLevel( user, workflow );

        Set<OrganisationUnit> userOrgUnits = user.getDataViewOrganisationUnitsWithFallback();

        boolean isDefaultCombo = attributeOptionCombos != null && attributeOptionCombos.size() == 1
            && categoryService.getDefaultCategoryOptionCombo().equals( attributeOptionCombos.toArray()[0] );

        boolean maySeeDefaultCategoryCombo =
            ( CollectionUtils.isEmpty( user.getUserCredentials().getCogsDimensionConstraints() )
                && CollectionUtils.isEmpty( user.getUserCredentials().getCatDimensionConstraints() ) );

        // ---------------------------------------------------------------------
        // Validate
        // ---------------------------------------------------------------------

        if ( isDefaultCombo && !maySeeDefaultCategoryCombo )
        {
            log.warn( "DefaultCategoryCombo selected but user " + user.getUsername() + " lacks permission to see it." );

            return new ArrayList<>(); // Unapprovable.
        }

        if ( CollectionUtils.isEmpty( approvalLevels ) )
        {
            log.warn( "No approval levels configured for workflow " + workflow.getName() );

            return new ArrayList<>(); // Unapprovable.
        }

        if ( CollectionUtils.isEmpty( userApprovalLevels ) )
        {
            log.warn( "No user approval levels for user " + user.getUsername() + ", workflow " + workflow.getName() );

            return new ArrayList<>(); // Unapprovable.
        }

        if ( orgUnits != null )
        {
            for ( OrganisationUnit orgUnit : orgUnits )
            {
                if ( !orgUnit.isDescendant( userOrgUnits ) )
                {
                    log.debug( "User " + user.getUsername() + " can't see orgUnit " + orgUnit.getName() );

                    return new ArrayList<>(); // Unapprovable.
                }
            }
        }

        // ---------------------------------------------------------------------
        // Get other information
        // ---------------------------------------------------------------------

        final boolean isSuperUser = currentUserService.currentUserIsSuper();

        final String startDate = DateUtils.getMediumDateString( period.getStartDate() );
        final String endDate = DateUtils.getMediumDateString( period.getEndDate() );

        DataApprovalLevel highestApprovalLevel = approvalLevels.get( 0 );
        DataApprovalLevel highestUserApprovalLevel = userApprovalLevels.get( 0 );

        DataApprovalLevel lowestApprovalLevelForOrgUnit = null;
        DataApprovalLevel approvalLevelAboveOrgUnit = null;
        DataApprovalLevel approvalLevelBelowOrgUnit = null;
        DataApprovalLevel approvalLevelAboveUser = null;

        if ( orgUnits == null )
        {
            orgUnitLevel = approvalLevels.get( approvalLevels.size() - 1 ).getOrgUnitLevel();
        }

        for ( DataApprovalLevel dal : approvalLevels )
        {
            int dalOrgUnitLevel = dal.getOrgUnitLevel();

            if ( dal.getLevel() < highestUserApprovalLevel.getLevel() )
            {
                approvalLevelAboveUser = dal;
            }

            if ( dalOrgUnitLevel < orgUnitLevel )
            {
                approvalLevelAboveOrgUnit = dal;
            }
            else if ( dal.getOrgUnitLevel() == orgUnitLevel )
            {
                lowestApprovalLevelForOrgUnit = dal;
            }
            else // dal.getOrgUnitLevel() > orgUnitLevel
            {
                approvalLevelBelowOrgUnit = dal;
                break;
            }
        }

        DataApprovalLevel approvedAboveLevel = null;

        if ( highestUserApprovalLevel.getLevel() != highestApprovalLevel.getLevel() &&
            ( orgUnits == null || orgUnitLevel == highestUserApprovalLevel.getOrgUnitLevel() ) )
        {
            approvedAboveLevel = approvalLevelAboveUser;
        }
        else if ( orgUnits != null && orgUnitLevel != highestUserApprovalLevel.getOrgUnitLevel() )
        {
            approvedAboveLevel = approvalLevelAboveOrgUnit;
        }

        log.debug( "Workflow '" + workflow.getName() + "' levels: " + approvalLevels.size() +
            ", user levels: " + userApprovalLevels.size() +
            ", lowestApprovalLevelForOrgUnit: " + ( lowestApprovalLevelForOrgUnit == null ? "-" : lowestApprovalLevelForOrgUnit.getLevel() ) +
            ", approvalLevelAboveOrgUnit: " + ( approvalLevelAboveOrgUnit == null ? "-" : approvalLevelAboveOrgUnit.getLevel() ) +
            ", approvalLevelBelowOrgUnit: " + ( approvalLevelBelowOrgUnit == null ? "-" : approvalLevelBelowOrgUnit.getLevel() ) +
            ", approvalLevelAboveUser: " + ( approvalLevelAboveUser == null ? "-" : approvalLevelAboveUser.getLevel() ) +
            ", approvedAboveLevel: " + ( approvedAboveLevel == null ? "-" : approvedAboveLevel.getLevel() ) );

        // ---------------------------------------------------------------------
        // Construct query
        // ---------------------------------------------------------------------

        String userOrgUnitRestrictions = "";

        if ( !isSuperUser && !userOrgUnits.isEmpty() )
        {
            for ( OrganisationUnit ou : userOrgUnits )
            {
                userOrgUnitRestrictions += ( userOrgUnitRestrictions.length() == 0 ? " and ( " : " or " )
                    + statementBuilder.position( "'" + ou.getUid() + "'", "o.path" ) + " <> 0";
            }
            userOrgUnitRestrictions += " )";
        }

        String highestApprovedOrgUnitJoin = "";
        String highestApprovedOrgUnitCompare;
        String orgUnitIds = "";

        if ( orgUnits != null )
        {
            orgUnitIds = StringUtils.join( IdentifiableObjectUtils.getIdentifiers( orgUnits ), "," );

            highestApprovedOrgUnitCompare = "da.organisationunitid in (" + orgUnitIds + ") ";
        }
        else
        {
            highestApprovedOrgUnitJoin = "join organisationunit dao on dao.organisationunitid = da.organisationunitid ";

            highestApprovedOrgUnitCompare = statementBuilder.position( "dao.uid", "o.path" ) + " <> 0";
        }

        String userApprovalLevelRestrictions = "";

        if ( !isSuperUser && userApprovalLevels.size() != approvalLevels.size() )
        {
            for ( DataApprovalLevel dal : userApprovalLevels )
            {
                userApprovalLevelRestrictions += ( userApprovalLevelRestrictions.length() == 0 ?
                    " and dal.dataapprovallevelid in ( " : ", " ) + dal.getId();
            }
            userApprovalLevelRestrictions += " ) ";
        }

        String approvedAboveSubquery = "false"; // Not approved above if this is the highest (lowest number) approval orgUnit level.

        if ( approvedAboveLevel != null )
        {
            approvedAboveSubquery = "exists(select 1 from dataapproval da " +
                "join period p on p.periodid = da.periodid " +
                "join organisationunit dao on dao.organisationunitid = da.organisationunitid " +
                "where " + statementBuilder.position( "dao.uid", "o.path" ) +" = " + pathPositionAtLevel( approvedAboveLevel ) + " " +
                "and '" + endDate + "' >= p.startdate and '" + endDate + "' <= p.enddate " +
                "and da.dataapprovallevelid = " + approvedAboveLevel.getId() + " " +
                "and da.workflowid = " + workflow.getId() + " and da.attributeoptioncomboid = cocco.categoryoptioncomboid)";
        }

        String readyBelowSubquery = "true"; // Ready below if this is the lowest (highest number) approval orgUnit level.

        if ( approvalLevelBelowOrgUnit != null )
        {
            boolean acceptanceRequiredForApproval = (Boolean) systemSettingManager.getSystemSetting( SettingKey.ACCEPTANCE_REQUIRED_FOR_APPROVAL );

            readyBelowSubquery = "not exists (select 1 from organisationunit dao " +
                "where exists (select 1 from organisationunit child " +
                    "where " + statementBuilder.position( "dao.uid", "child.path" ) + " <> 0 " +
                    "and child.organisationunitid in (select distinct sourceid from datasetsource dss join dataset ds on ds.datasetid = dss.datasetid where ds.workflowid = " + workflow.getId() + ")) " +
                "and not exists (select 1 from dataapproval da " +
                    "join period p on p.periodid = da.periodid " +
                    "where da.organisationunitid = dao.organisationunitid " +
                    "and da.dataapprovallevelid = " + approvalLevelBelowOrgUnit.getId() + " " +
                    "and '" + endDate + "' >= p.startdate and '" + endDate + "' <= p.enddate " +
                    "and da.workflowid = " + workflow.getId() + " " +
                    "and da.attributeoptioncomboid = cocco.categoryoptioncomboid " +
                    ( acceptanceRequiredForApproval ? "and da.accepted " : "" ) +
                ") " +
                "and " + statementBuilder.position( "o.uid", "dao.path" ) + " = " + pathPositionAtLevel( orgUnitLevel ) + " " +
                "and dao.hierarchylevel = " + approvalLevelBelowOrgUnit.getOrgUnitLevel() + " " +
                ( isDefaultCombo ? "" :
                    "and ( not exists ( select 1 from categoryoption_organisationunits c_o where c_o.categoryoptionid = cocco.categoryoptionid ) " +
                        "or exists ( select 1 from categoryoption_organisationunits c_o " +
                        "join organisationunit o2 on o2.organisationunitid = c_o.organisationunitid " +
                        "where c_o.categoryoptionid = cocco.categoryoptionid and " + statementBuilder.position( "o2.uid", "dao.path" ) +
                        " between 2 and " + pathPositionAtLevel( approvalLevelBelowOrgUnit ) + ") ) " ) +
                ")";
        }

        final String sql =
            "select coc.uid as cocuid, o.uid as ouuid, o.name as ouname, " +
            "(select min(" + statementBuilder.concatenate( MAX_APPROVAL_LEVEL + " + dal.level", SQL_CAT, "da.accepted", SQL_CAT, "da.organisationunitid" ) + ") " +
                "from dataapproval da " +
                "join dataapprovallevel dal on dal.dataapprovallevelid = da.dataapprovallevelid " +
                highestApprovedOrgUnitJoin +
                "where da.workflowid = " + workflow.getId() + " " +
                "and da.periodid = " + getWorkflowPeriodId( workflow, endDate ) + " " +
                "and da.attributeoptioncomboid = cocco.categoryoptioncomboid " +
                "and " + highestApprovedOrgUnitCompare + userApprovalLevelRestrictions +
            ") as highest_approved, " +
            readyBelowSubquery + " as ready_below, " +
            approvedAboveSubquery + " as approved_above " +
            "from categoryoptioncombo coc " +
            "join categoryoptioncombos_categoryoptions cocco on cocco.categoryoptioncomboid = coc.categoryoptioncomboid " +
            ( attributeCombo == null ? "" : "join categorycombos_optioncombos ccoc on ccoc.categoryoptioncomboid = cocco.categoryoptioncomboid " +
                "and ccoc.categorycomboid = " + attributeCombo.getId() + " " ) +
            "join dataelementcategoryoption co on co.categoryoptionid = cocco.categoryoptionid " +
                "and (co.startdate is null or co.startdate <= '" + endDate + "') and (co.enddate is null or co.enddate >= '" + startDate + "') " +
            "join organisationunit o on " + (orgUnits != null ? "o.organisationunitid in (" + orgUnitIds + ")" : "o.hierarchylevel = " + orgUnitLevel + userOrgUnitRestrictions ) + " " +
            "left join categoryoption_organisationunits coo on coo.categoryoptionid = co.categoryoptionid " +
            "left join organisationunit oc on oc.organisationunitid = coo.organisationunitid " +
            "where ( coo.categoryoptionid is null or " +
                statementBuilder.position( "o.uid", "oc.path" ) + " <> 0  or " +
                statementBuilder.position( "oc.uid", "o.path" ) + " <> 0 )" +
            ( attributeOptionCombos == null || attributeOptionCombos.isEmpty() ? "" : " and cocco.categoryoptioncomboid in (" +
                StringUtils.join( IdentifiableObjectUtils.getIdentifiers( attributeOptionCombos ), "," ) + ") " ) +
            ( isSuperUser ? "" :
                " and ( co.publicaccess is null or left(co.publicaccess, 1) = 'r' or co.userid is null or co.userid = " + user.getId() + " or exists ( " +
                "select 1 from dataelementcategoryoptionusergroupaccesses couga " +
                "left join usergroupaccess uga on uga.usergroupaccessid = couga.usergroupaccessid " +
                "left join usergroupmembers ugm on ugm.usergroupid = uga.usergroupid " +
                    "where couga.categoryoptionid = cocco.categoryoptionid and ugm.userid = " + user.getId() + ") ) " ) +
                " and exists (select 1 from organisationunit od where od.path like o.path || '%' and od.organisationunitid in " +
                "(select distinct sourceid from datasetsource dss join dataset ds on ds.datasetid = dss.datasetid where ds.workflowid = " + workflow.getId() + "))";

        log.debug( "User " + user.getUsername() + " superuser " + isSuperUser
            + " workflow " + workflow.getName() + " period " + period.getIsoDate()
            + " orgUnits " + ( orgUnits == null ? "null" : orgUnits )
            + " attributeCombo " + ( attributeCombo == null ? "null" : attributeCombo.getName() ) );

        log.debug( "Get approval SQL: " + sql );

        // ---------------------------------------------------------------------
        // Fetch query results and process them
        // ---------------------------------------------------------------------

        SqlRowSet rowSet = jdbcTemplate.queryForRowSet( sql );

        Map<Integer, DataApprovalLevel> levelMap = dataApprovalLevelService.getDataApprovalLevelMap();

        List<DataApprovalStatus> statusList = new ArrayList<>();

        while ( rowSet.next() )
        {
            final String aocUid = rowSet.getString( 1 );
            final String ouUid = rowSet.getString( 2 );
            final String ouName = rowSet.getString( 3 );
            final String highestApproved = rowSet.getString( 4 );
            final boolean readyBelow = rowSet.getBoolean( 5 );
            boolean approvedAbove = rowSet.getBoolean( 6 );

            final String[] approved = highestApproved == null ? null : highestApproved.split( SQL_CONCAT );
            final int level = approved == null ? 0 : Integer.parseInt( approved[ 0 ] ) - MAX_APPROVAL_LEVEL;
            final boolean accepted = approved == null ? false : approved[ 1 ].substring( 0, 1 ).equalsIgnoreCase( "t" );
            final int approvedOrgUnitId = approved == null ? 0 : Integer.parseInt( approved[ 2 ] );

            DataApprovalLevel approvedLevel = ( level == 0 ? null : levelMap.get( level ) ); // null if not approved
            DataApprovalLevel actionLevel = ( approvedLevel == null ? lowestApprovalLevelForOrgUnit : approvedLevel );

            if ( approvedAbove && accepted && approvedAboveLevel == approvalLevelAboveUser )
            {
                approvedAbove = false; // Hide higher-level approval from user.
            }

            if ( ouUid != null )
            {
                DataApprovalState state = (
                    approvedAbove ?
                        APPROVED_ABOVE :
                        approvedLevel == null ?
                            lowestApprovalLevelForOrgUnit == null ?
                                approvalLevelAboveOrgUnit == null ?
                                    UNAPPROVABLE :
                                    UNAPPROVED_ABOVE :
                                readyBelow ?
                                    UNAPPROVED_READY :
                                    UNAPPROVED_WAITING :
                            accepted ?
                                ACCEPTED_HERE :
                                APPROVED_HERE );

                statusList.add( new DataApprovalStatus( state, approvedLevel, approvedOrgUnitId, actionLevel, ouUid, ouName, aocUid, accepted, null ) );
            }
        }

        return statusList;
    }

    /**
     * Get the id for the workflow period that spans the given end date.
     * The workflow period may or may not be the same as the period for which
     * we are checking data validity. The workflow period will have a period
     * type that matches the workflow period type, and it will contain the
     * end date of the period for which we are checking data validity.
     *
     * Returns zero if there is no such workflow period.
     *
     * It turns out that this is much faster done as a separate query in
     * postgresql than imbedding this as a subquery in the larger query above.
     *
     * @param workflow workflow we are checking
     * @param endDate end date of the period we are checking approval for,
     *                formatted as a string for a SQL query.
     * @return id of the workflow period which overlaps with the endDate
     */
    private int getWorkflowPeriodId( DataApprovalWorkflow workflow, String endDate )
    {
        final String sql = "select periodid from period where '" + endDate + "' >= startdate and '" + endDate + "' <= enddate and periodtypeid = " + workflow.getPeriodType().getId();

        SqlRowSet rowSet = jdbcTemplate.queryForRowSet( sql );

        if ( rowSet.next() )
        {
            return rowSet.getInt( 1 );
        }

        return 0;
    }

    //TODO: Should we move these two methods to static methods in OrganisationUnit?
    /**
     * Returns the position within an orgUnit path at which the orgUnit UID
     * will be found for a given orgUnitLevel.
     *
     * @param orgUnitLevel organization unit level.
     * @return position within path for this org unit level.
     */
    private int pathPositionAtLevel( int orgUnitLevel )
    {
        return ( orgUnitLevel - 1 ) * 12 + 2;
    }

    /**
     * Returns the position within an orgUnit path at which the orgUnit UID
     * will be found for a given data approval level.
     *
     * @param level data approval level.
     * @return position within path for this org unit level.
     */
    private int pathPositionAtLevel( DataApprovalLevel level )
    {
        return pathPositionAtLevel( level.getOrgUnitLevel() );
    }
}
