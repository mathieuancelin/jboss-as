/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.connector.pool;

import static org.jboss.as.connector.ConnectorMessages.MESSAGES;
import static org.jboss.as.connector.pool.Constants.BACKGROUNDVALIDATION;
import static org.jboss.as.connector.pool.Constants.BACKGROUNDVALIDATIONMILLIS;
import static org.jboss.as.connector.pool.Constants.BLOCKING_TIMEOUT_WAIT_MILLIS;
import static org.jboss.as.connector.pool.Constants.IDLETIMEOUTMINUTES;
import static org.jboss.as.connector.pool.Constants.MAX_POOL_SIZE;
import static org.jboss.as.connector.pool.Constants.MIN_POOL_SIZE;
import static org.jboss.as.connector.pool.Constants.POOL_FLUSH_STRATEGY;
import static org.jboss.as.connector.pool.Constants.POOL_PREFILL;
import static org.jboss.as.connector.pool.Constants.POOL_USE_STRICT_MIN;
import static org.jboss.as.connector.pool.Constants.USE_FAST_FAIL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.connector.ConnectorServices;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.server.operations.ServerWriteAttributeOperationHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.jca.core.api.connectionmanager.pool.PoolConfiguration;
import org.jboss.jca.core.api.management.Connector;
import org.jboss.jca.core.api.management.DataSource;
import org.jboss.jca.core.api.management.ManagementRepository;
import org.jboss.msc.service.ServiceController;

/**
 * @author <a href="mailto:stefano.maestri@redhat.com">Stefano Maestri</a>
 * @author <a href="mailto:jeff.zhang@jboss.org">Jeff Zhang</a>
 */
public class PoolConfigurationRWHandler {

    static final String[] NO_LOCATION = new String[0];

    public static final String[] ATTRIBUTES = new String[]{MAX_POOL_SIZE.getName(), MIN_POOL_SIZE.getName(), BLOCKING_TIMEOUT_WAIT_MILLIS.getName(),
            IDLETIMEOUTMINUTES.getName(), BACKGROUNDVALIDATION.getName(), BACKGROUNDVALIDATIONMILLIS.getName(),
            POOL_PREFILL.getName(), POOL_USE_STRICT_MIN.getName(), POOL_FLUSH_STRATEGY.getName()};

    // TODO this seems to just do what the default handler does, so registering it is probably unnecessary
    public static class PoolConfigurationReadHandler implements OperationStepHandler {
        public static PoolConfigurationReadHandler INSTANCE = new PoolConfigurationReadHandler();

        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            final String parameterName = operation.require(NAME).asString();

            final ModelNode submodel = context.readModel(PathAddress.EMPTY_ADDRESS);
            final ModelNode currentValue = submodel.hasDefined(parameterName) ? submodel.get(parameterName).clone() : new ModelNode();

            context.getResult().set(currentValue);

            context.completeStep();
        }
    }

    public abstract static class PoolConfigurationWriteHandler extends ServerWriteAttributeOperationHandler {

        protected PoolConfigurationWriteHandler(ParameterValidator validator) {
            super(validator);
        }

        @Override
        protected boolean applyUpdateToRuntime(final OperationContext context, final ModelNode operation,
               final String parameterName, final ModelNode newValue,
               final ModelNode currentValue) throws OperationFailedException {

            if (context.getType() == OperationContext.Type.SERVER) {
                context.addStep(new OperationStepHandler() {
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
                        final String jndiName = address.getLastElement().getValue();

                        final ServiceController<?> managementRepoService = context.getServiceRegistry(false).getService(
                                ConnectorServices.MANAGEMENT_REPOSISTORY_SERVICE);
                        List<PoolConfiguration> poolConfigs = null;
                        if (managementRepoService != null) {
                            try {
                                final ManagementRepository repository = (ManagementRepository) managementRepoService.getValue();
                                poolConfigs = getMatchingPoolConfigs(jndiName, repository);
                                updatePoolConfigs(poolConfigs, parameterName, newValue);
                            } catch (Exception e) {
                                throw new OperationFailedException(new ModelNode().set(MESSAGES.failedToSetAttribute(e.getLocalizedMessage())));
                            }
                        }

                        if (context.completeStep() == OperationContext.ResultAction.ROLLBACK && poolConfigs != null) {
                            updatePoolConfigs(poolConfigs, parameterName, currentValue);
                        }

                    }
                }, OperationContext.Stage.RUNTIME);
            }

            return ( IDLETIMEOUTMINUTES.getName().equals(parameterName) ||  BACKGROUNDVALIDATION.getName().equals(parameterName)
                    ||  BACKGROUNDVALIDATIONMILLIS.getName().equals(parameterName)
                    ||  POOL_PREFILL.getName().equals(parameterName));

        }

        private void updatePoolConfigs(List<PoolConfiguration> poolConfigs, String parameterName, ModelNode newValue) {
            for (PoolConfiguration pc : poolConfigs) {
                if (MAX_POOL_SIZE.getName().equals(parameterName)) {
                    pc.setMaxSize(newValue.asInt());
                }
                if ( MIN_POOL_SIZE.getName().equals(parameterName)) {
                    pc.setMinSize(newValue.asInt());
                }
                if ( BLOCKING_TIMEOUT_WAIT_MILLIS.getName().equals(parameterName)) {
                    pc.setBlockingTimeout(newValue.asLong());
                }
                if ( POOL_USE_STRICT_MIN.getName().equals(parameterName)) {
                    pc.setStrictMin(newValue.asBoolean());
                }
                if ( USE_FAST_FAIL.getName().equals(parameterName)) {
                    pc.setUseFastFail(newValue.asBoolean());
                }
            }
        }

        protected abstract List<PoolConfiguration> getMatchingPoolConfigs(String jndiName, ManagementRepository repository);
    }

    public static class LocalAndXaDataSourcePoolConfigurationWriteHandler extends PoolConfigurationWriteHandler {
        public static LocalAndXaDataSourcePoolConfigurationWriteHandler INSTANCE = new LocalAndXaDataSourcePoolConfigurationWriteHandler(
                new PoolConfigurationValidator());

        protected LocalAndXaDataSourcePoolConfigurationWriteHandler(ParameterValidator validator) {
            super(validator);
        }

        protected List<PoolConfiguration> getMatchingPoolConfigs(String jndiName, ManagementRepository repository) {
            ArrayList<PoolConfiguration> result = new ArrayList<PoolConfiguration>(repository.getDataSources().size());
            if (repository.getDataSources() != null) {
                for (DataSource ds : repository.getDataSources()) {
                    if (jndiName.equalsIgnoreCase(ds.getJndiName())) {
                        result.add(ds.getPoolConfiguration());
                    }

                }
            }
            result.trimToSize();
            return result;
        }

    }

    public static class RaPoolConfigurationWriteHandler extends PoolConfigurationWriteHandler {
        public static RaPoolConfigurationWriteHandler INSTANCE = new RaPoolConfigurationWriteHandler(
                new PoolConfigurationValidator());

        protected RaPoolConfigurationWriteHandler(ParameterValidator validator) {
            super(validator);
        }

        protected List<PoolConfiguration> getMatchingPoolConfigs(String jndiName, ManagementRepository repository) {
            ArrayList<PoolConfiguration> result = new ArrayList<PoolConfiguration>(repository.getConnectors().size());
            if (repository.getConnectors() != null) {
                for (Connector conn : repository.getConnectors()) {
                    if (jndiName.equalsIgnoreCase(conn.getUniqueId())) {
                        if (conn.getConnectionFactories() == null || conn.getConnectionFactories().get(0) == null)
                            continue;
                        PoolConfiguration pc = conn.getConnectionFactories().get(0).getPoolConfiguration();
                        result.add(pc);
                    }

                }
            }
            result.trimToSize();
            return result;
        }

    }

    static class PoolConfigurationValidator implements ParameterValidator {

        static final ModelTypeValidator intValidator = new ModelTypeValidator(ModelType.INT);
        static final ModelTypeValidator longValidator = new ModelTypeValidator(ModelType.LONG);
        static final ModelTypeValidator boolValidator = new ModelTypeValidator(ModelType.BOOLEAN);

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {

            if (MAX_POOL_SIZE.getName().equals(parameterName)) {
                intValidator.validateParameter(parameterName, value);
            } else if ( MIN_POOL_SIZE.getName().equals(parameterName)) {
                intValidator.validateParameter(parameterName, value);
            } else if ( BLOCKING_TIMEOUT_WAIT_MILLIS.getName().equals(parameterName)) {
                longValidator.validateParameter(parameterName, value);
            } else if ( IDLETIMEOUTMINUTES.getName().equals(parameterName)) {
                longValidator.validateParameter(parameterName, value);
            } else if ( BACKGROUNDVALIDATION.getName().equals(parameterName)) {
                boolValidator.validateParameter(parameterName, value);
            } else if ( BACKGROUNDVALIDATIONMILLIS.getName().equals(parameterName)) {
                longValidator.validateParameter(parameterName, value);
            } else if ( POOL_PREFILL.getName().equals(parameterName)) {
                boolValidator.validateParameter(parameterName, value);
            } else if ( POOL_USE_STRICT_MIN.getName().equals(parameterName)) {
                boolValidator.validateParameter(parameterName, value);
            } else if ( USE_FAST_FAIL.getName().equals(parameterName)) {
                boolValidator.validateParameter(parameterName, value);
            } else {
                throw new OperationFailedException(new ModelNode().set(MESSAGES.invalidParameterName(parameterName)));
            }

        }

        @Override
        public void validateResolvedParameter(String parameterName, ModelNode value) throws OperationFailedException {
            validateParameter(parameterName, value.resolve());
        }

    }
}
