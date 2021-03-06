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

package org.jboss.as.ejb3.subsystem;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.server.operations.ServerWriteAttributeOperationHandler;
import org.jboss.dmr.ModelNode;

/**
 * Handles the "write-attribute" operation for a strict-max-bean-instance-pool resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class StrictMaxPoolWriteHandler extends ServerWriteAttributeOperationHandler {

    public static final StrictMaxPoolWriteHandler INSTANCE = new StrictMaxPoolWriteHandler();

    private StrictMaxPoolWriteHandler() {
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                           ModelNode newValue, ModelNode currentValue) throws OperationFailedException {

        final boolean restartAllowed = context.isResourceServiceRestartAllowed();
        if (restartAllowed) {
            final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
            StrictMaxPoolRemove.INSTANCE.removeRuntimeService(context, operation);
            StrictMaxPoolAdd.INSTANCE.installRuntimeService(context, model, new ServiceVerificationHandler());
        }

        return !restartAllowed;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert) throws OperationFailedException {
        final ModelNode restored = context.readResource(PathAddress.EMPTY_ADDRESS).getModel().clone();
        restored.get(attributeName).set(valueToRestore);
        StrictMaxPoolRemove.INSTANCE.removeRuntimeService(context, operation);
        StrictMaxPoolAdd.INSTANCE.installRuntimeService(context, restored, null);
    }

    @Override
    protected void validateValue(String name, ModelNode value) throws OperationFailedException {
        StrictMaxPoolResourceDefinition.ATTRIBUTES.get(name).getValidator().validateParameter(ModelDescriptionConstants.VALUE, value);
    }

    @Override
    protected void validateResolvedValue(String name, ModelNode value) throws OperationFailedException {
        StrictMaxPoolResourceDefinition.ATTRIBUTES.get(name).getValidator().validateResolvedParameter(ModelDescriptionConstants.VALUE, value);
    }
}
