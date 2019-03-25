/*
 * Copyright (c) 2004-2019, University of Oslo
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

package org.hisp.dhis.config;

import org.hibernate.SessionFactory;
import org.hisp.dhis.chart.Chart;
import org.hisp.dhis.color.ColorSet;
import org.hisp.dhis.common.hibernate.HibernateAnalyticalObjectStore;
import org.hisp.dhis.common.hibernate.HibernateIdentifiableObjectStore;
import org.hisp.dhis.constant.Constant;
import org.hisp.dhis.dashboard.Dashboard;
import org.hisp.dhis.dataapproval.DataApprovalAudit;
import org.hisp.dhis.dataapproval.DataApprovalLevel;
import org.hisp.dhis.dataapproval.DataApprovalWorkflow;
import org.hisp.dhis.dataapproval.hibernate.HibernateDataApprovalAuditStore;
import org.hisp.dhis.dataapproval.hibernate.HibernateDataApprovalLevelStore;
import org.hisp.dhis.dataapproval.hibernate.HibernateDataApprovalWorkflowStore;
import org.hisp.dhis.dataset.DataSet;
import org.hisp.dhis.dataset.Section;
import org.hisp.dhis.dataset.hibernate.HibernateDataSetStore;
import org.hisp.dhis.dataset.hibernate.HibernateSectionStore;
import org.hisp.dhis.dataset.notifications.DataSetNotificationTemplate;
import org.hisp.dhis.dataset.notifications.HibernateDataSetNotificationTemplateStore;
import org.hisp.dhis.deletedobject.DeletedObjectService;
import org.hisp.dhis.eventchart.EventChart;
import org.hisp.dhis.eventreport.EventReport;
import org.hisp.dhis.expression.Expression;
import org.hisp.dhis.fileresource.FileResource;
import org.hisp.dhis.hibernate.HibernateGenericStore;
import org.hisp.dhis.indicator.IndicatorGroup;
import org.hisp.dhis.indicator.IndicatorGroupSet;
import org.hisp.dhis.indicator.IndicatorType;
import org.hisp.dhis.keyjsonvalue.KeyJsonValue;
import org.hisp.dhis.keyjsonvalue.hibernate.HibernateKeyJsonValueStore;
import org.hisp.dhis.legend.LegendSet;
import org.hisp.dhis.option.OptionSet;
import org.hisp.dhis.period.PeriodService;
import org.hisp.dhis.predictor.PredictorGroup;
import org.hisp.dhis.program.ProgramExpression;
import org.hisp.dhis.program.ProgramIndicatorGroup;
import org.hisp.dhis.program.notification.ProgramNotificationInstance;
import org.hisp.dhis.program.notification.ProgramNotificationTemplate;
import org.hisp.dhis.report.Report;
import org.hisp.dhis.reporttable.ReportTable;
import org.hisp.dhis.scheduling.JobConfiguration;
import org.hisp.dhis.security.acl.AclService;
import org.hisp.dhis.setting.hibernate.HibernateSystemSettingStore;
import org.hisp.dhis.user.CurrentUserService;
import org.hisp.dhis.user.UserAccess;
import org.hisp.dhis.user.UserGroup;
import org.hisp.dhis.user.UserGroupAccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author Luciano Fiandesio
 */
@Configuration("coreStoreConfig")
public class StoreConfig
{
    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private DeletedObjectService deletedObjectService;

    @Autowired
    private AclService aclService;

    @Autowired
    private PeriodService periodService;

    @Bean( "org.hisp.dhis.indicator.IndicatorTypeStore" )
    public HibernateIdentifiableObjectStore indicatorTypeStore()
    {
        HibernateIdentifiableObjectStore<IndicatorType> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
                jdbcTemplate, IndicatorType.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.indicator.IndicatorGroupStore" )
    public HibernateIdentifiableObjectStore indicatorGroupStore()
    {
        HibernateIdentifiableObjectStore<IndicatorGroup> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
                jdbcTemplate, IndicatorGroup.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.indicator.IndicatorGroupSetStore" )
    public HibernateIdentifiableObjectStore indicatorGroupSetStore()
    {
        HibernateIdentifiableObjectStore<IndicatorGroupSet> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
                jdbcTemplate, IndicatorGroupSet.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.predictor.PredictorGroupStore" )
    public HibernateIdentifiableObjectStore predictorGroupStore()
    {
        HibernateIdentifiableObjectStore<PredictorGroup> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
                jdbcTemplate, PredictorGroup.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.expression.ExpressionStore" )
    public HibernateGenericStore expressionStore()
    {
        HibernateGenericStore<Expression> store = new HibernateGenericStore<>( sessionFactory,
                jdbcTemplate, Expression.class );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.user.UserGroupStore" )
    public HibernateIdentifiableObjectStore userGroupStore()
    {
        HibernateIdentifiableObjectStore<UserGroup> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
                jdbcTemplate, UserGroup.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.user.UserGroupAccessStore" )
    public HibernateGenericStore userGroupAccessStore()
    {
        HibernateGenericStore<UserGroupAccess> store = new HibernateGenericStore<>( sessionFactory,
                jdbcTemplate, UserGroupAccess.class );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.user.UserAccessStore" )
    public HibernateGenericStore userAccessStore()
    {
        HibernateGenericStore<UserAccess> store = new HibernateGenericStore<>( sessionFactory,
                jdbcTemplate, UserAccess.class );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.configuration.ConfigurationStore" )
    public HibernateGenericStore configurationStore()
    {
        HibernateGenericStore<org.hisp.dhis.configuration.Configuration> store = new HibernateGenericStore<>( sessionFactory,
                jdbcTemplate, org.hisp.dhis.configuration.Configuration.class );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.constant.ConstantStore" )
    public HibernateIdentifiableObjectStore constantStore()
    {
        HibernateIdentifiableObjectStore<Constant> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
                jdbcTemplate, Constant.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.scheduling.JobConfigurationStore" )
    public HibernateIdentifiableObjectStore jobConfigurationStore()
    {
        HibernateIdentifiableObjectStore<JobConfiguration> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
                jdbcTemplate, JobConfiguration.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.option.OptionSetStore" )
    public HibernateIdentifiableObjectStore optionSetStore()
    {
        HibernateIdentifiableObjectStore<OptionSet> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
                jdbcTemplate, OptionSet.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.legend.LegendSetStore" )
    public HibernateIdentifiableObjectStore legendSetStore()
    {
        HibernateIdentifiableObjectStore<LegendSet> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
                jdbcTemplate, LegendSet.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.program.ProgramIndicatorGroupStore" )
    public HibernateIdentifiableObjectStore programIndicatorGroupStore()
    {
        HibernateIdentifiableObjectStore<ProgramIndicatorGroup> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
                jdbcTemplate, ProgramIndicatorGroup.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.report.ReportStore" )
    public HibernateIdentifiableObjectStore reportStore()
    {
        HibernateIdentifiableObjectStore<Report> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
            jdbcTemplate, Report.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.chart.ChartStore" )
    public HibernateAnalyticalObjectStore chartStore()
    {
        HibernateAnalyticalObjectStore<Chart> store = new HibernateAnalyticalObjectStore<>( sessionFactory,
            jdbcTemplate, Chart.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.reporttable.ReportTableStore" )
    public HibernateAnalyticalObjectStore reportTableStore()
    {
        HibernateAnalyticalObjectStore<ReportTable> store = new HibernateAnalyticalObjectStore<>( sessionFactory,
            jdbcTemplate, ReportTable.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.dashboard.DashboardStore" )
    public HibernateIdentifiableObjectStore dashboardStore()
    {
        HibernateIdentifiableObjectStore<Dashboard> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
            jdbcTemplate, Dashboard.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.setting.SystemSettingStore" )
    public HibernateSystemSettingStore systemSettingStore()
    {
        HibernateSystemSettingStore store = new HibernateSystemSettingStore( sessionFactory, jdbcTemplate );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.dataapproval.DataApprovalAuditStore" )
    public HibernateDataApprovalAuditStore dataApprovalAuditStore()
    {
        HibernateDataApprovalAuditStore store = new HibernateDataApprovalAuditStore( sessionFactory, jdbcTemplate,
                DataApprovalAudit.class, currentUserService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.dataapproval.DataApprovalLevelStore" )
    public HibernateDataApprovalLevelStore dataApprovalLevelStore()
    {
        HibernateDataApprovalLevelStore store = new HibernateDataApprovalLevelStore( sessionFactory, jdbcTemplate,
                DataApprovalLevel.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.dataapproval.DataApprovalWorkflowStore" )
    public HibernateDataApprovalWorkflowStore dataApprovalWorkflowStore()
    {
        HibernateDataApprovalWorkflowStore store = new HibernateDataApprovalWorkflowStore( sessionFactory, jdbcTemplate,
                DataApprovalWorkflow.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.dataset.DataSetStore" )
    public HibernateDataSetStore hibernateDataSetStore()
    {
        // TODO a repository class should not invoke a service
        HibernateDataSetStore store = new HibernateDataSetStore( sessionFactory, jdbcTemplate,
                DataSet.class, currentUserService, deletedObjectService, aclService, periodService);
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.dataset.SectionStore")
    public HibernateSectionStore sectionStore()
    {
        HibernateSectionStore store = new HibernateSectionStore( sessionFactory, jdbcTemplate,
                Section.class, currentUserService, deletedObjectService, aclService);
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.dataset.notifications.DataSetNotificationTemplateStore")
    public HibernateDataSetNotificationTemplateStore dataSetNotificationTemplateStore()
    {
        HibernateDataSetNotificationTemplateStore store = new HibernateDataSetNotificationTemplateStore( sessionFactory, jdbcTemplate,
                DataSetNotificationTemplate.class, currentUserService, deletedObjectService, aclService);
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.keyjsonvalue.KeyJsonValueStore")
    public HibernateKeyJsonValueStore keyJsonValueStore()
    {
        HibernateKeyJsonValueStore store = new HibernateKeyJsonValueStore( sessionFactory, jdbcTemplate,
                KeyJsonValue.class, currentUserService, deletedObjectService, aclService);
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.program.ProgramExpressionStore" )
    public HibernateGenericStore programExpressionStore()
    {
        HibernateGenericStore<ProgramExpression> store = new HibernateGenericStore<>( sessionFactory,
                jdbcTemplate, ProgramExpression.class );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.eventreport.EventReportStore" )
    public HibernateAnalyticalObjectStore eventReportStore()
    {
        HibernateAnalyticalObjectStore<EventReport> store = new HibernateAnalyticalObjectStore<>( sessionFactory,
                jdbcTemplate, EventReport.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.eventchart.EventChartStore" )
    public HibernateAnalyticalObjectStore eventChartStore()
    {
        HibernateAnalyticalObjectStore<EventChart> store = new HibernateAnalyticalObjectStore<>( sessionFactory,
                jdbcTemplate, EventChart.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.color.ColorSetStore" )
    public HibernateIdentifiableObjectStore colorSetStore()
    {
        HibernateIdentifiableObjectStore<ColorSet> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
                jdbcTemplate, ColorSet.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.fileresource.FileResourceStore" )
    public HibernateIdentifiableObjectStore fileResourceStore()
    {
        return new HibernateIdentifiableObjectStore<>( sessionFactory,
                jdbcTemplate, FileResource.class, currentUserService, deletedObjectService, aclService );
    }

    @Bean( "org.hisp.dhis.program.notification.ProgramNotificationStore" )
    public HibernateIdentifiableObjectStore programNotificationStore()
    {
        HibernateIdentifiableObjectStore<ProgramNotificationTemplate> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
                jdbcTemplate, ProgramNotificationTemplate.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }

    @Bean( "org.hisp.dhis.program.notification.ProgramNotificationInstanceStore" )
    public HibernateIdentifiableObjectStore programNotificationInstanceStore()
    {
        HibernateIdentifiableObjectStore<ProgramNotificationInstance> store = new HibernateIdentifiableObjectStore<>( sessionFactory,
                jdbcTemplate, ProgramNotificationInstance.class, currentUserService, deletedObjectService, aclService );
        store.setCacheable( true );
        return store;
    }
}
