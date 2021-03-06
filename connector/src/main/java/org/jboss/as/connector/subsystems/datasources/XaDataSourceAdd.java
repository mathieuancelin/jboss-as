/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.connector.subsystems.datasources;

import static org.jboss.as.connector.ConnectorMessages.MESSAGES;
import static org.jboss.as.connector.subsystems.datasources.Constants.XADATASOURCE_PROPERTIES;
import static org.jboss.as.connector.subsystems.datasources.DataSourceModelNodeUtil.xaFrom;
import static org.jboss.as.connector.subsystems.datasources.DataSourcesSubsystemProviders.XA_DATASOURCE_ATTRIBUTE;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.jca.common.api.metadata.ds.XaDataSource;
import org.jboss.jca.common.api.validator.ValidateException;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * Operation handler responsible for adding a XA data-source.
 * @author John Bailey
 * @author Stefano Maestri
 */
public class XaDataSourceAdd extends AbstractDataSourceAdd {
    static final XaDataSourceAdd INSTANCE = new XaDataSourceAdd();

    protected void populateModel(ModelNode operation, ModelNode model) {
        populateAddModel(operation, model, XADATASOURCE_PROPERTIES.getName(), XA_DATASOURCE_ATTRIBUTE);
    }

    protected AbstractDataSourceService createDataSourceService(final String jndiName) throws OperationFailedException {

        XaDataSourceService service = new XaDataSourceService(jndiName);
        return service;
    }

    @Override
    protected ServiceController<?> startConfigAndAddDependency(ServiceBuilder<?> dataSourceServiceBuilder,
            AbstractDataSourceService dataSourceService, String jndiName, ServiceTarget serviceTarget, final ModelNode operation)
            throws OperationFailedException {
        final XaDataSource dataSourceConfig;
        try {
            dataSourceConfig = xaFrom(operation);
        } catch (ValidateException e) {
            throw new OperationFailedException(e, new ModelNode().set(MESSAGES.failedToCreate("XaDataSource", operation, e.getLocalizedMessage())));
        }
        final ServiceName dataSourceCongServiceName = XADataSourceConfigService.SERVICE_NAME_BASE.append(jndiName);
        final XADataSourceConfigService configService = new XADataSourceConfigService(dataSourceConfig);

        ServiceController<?> svcController = serviceTarget.addService(dataSourceCongServiceName, configService).setInitialMode(Mode.ACTIVE).install();

        dataSourceServiceBuilder.addDependency(dataSourceCongServiceName, XaDataSource.class,
                ((XaDataSourceService) dataSourceService).getDataSourceConfigInjector());
        return svcController;
    }
}
