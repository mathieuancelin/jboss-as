package org.jboss.as.weld;

import java.io.InputStream;
import java.util.Properties;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.vfs.VirtualFile;

public class WeldOSGiDeploymentMarker {

    public static boolean isWeldOSGiDeployment(DeploymentUnit deploymentUnit) {
        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        try {
            VirtualFile f = deploymentRoot.getRoot().getChild("META-INF/MANIFEST.MF");
            if (f != null) {
                Properties manifest = new Properties();
                InputStream mStream = f.openStream();
                manifest.load(mStream);
                if (manifest.getProperty("Weld-OSGi-Deployment") != null) {
                    if (manifest.getProperty("Weld-OSGi-Deployment").equalsIgnoreCase("true")) {
                        mStream.close();
                        return true;
                    }
                }
                mStream.close();
            }
        } catch (Exception e) {
            // ignore it, Weld-OSGi hack
        }
        return false;
    }
}