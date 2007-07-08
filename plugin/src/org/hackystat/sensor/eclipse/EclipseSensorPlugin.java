package org.hackystat.sensor.eclipse;

import java.net.URL;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.hackystat.core.kernel.admin.SensorProperties;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * Provides a main plug-in functionality for Eclipse, namely instantiation of the
 * <code>org.hackystat.sensor.eclipse.EclipseSensor</code> class to start gathering
 * necessary data.
 * <p>
 * Since <code>earlyStartup()</code> method was called when Eclipse runs, there is a major
 * instantiation in the method such as instantiation of EclipseSensor.
 * 
 * Please note that resource bundle is defined since Eclipse ver3. Basically bundle is 
 * interchangable with plugin. 
 *
 * @author Takuya Yamashita
 * @version $Id: EclipseSensorPlugin.java,v 1.1.1.1 2005/10/20 23:56:56 johnson Exp $
 */
public class EclipseSensorPlugin extends AbstractUIPlugin implements IStartup {
  /** The shared instance. */
  private static EclipseSensorPlugin plugin;

  
  /**
   * Creates an Hackystat sensor plug-in runtime object for the given plug-in descriptor.
   * <p>Note that instances of plug-in runtime classes are automatically created by
   * the platform in the course of plug-in activation.
   *
   */
  public EclipseSensorPlugin() {
    super();
    EclipseSensorPlugin.plugin = this;
  }
  
  /**
   * Reimplement start to handle Eclipse plugin version check.
   * 
   * @param context Bundle context for Hackystat sensor.
   * @throws Exception If error while starting hackystat sensor.
   */
  public void start(BundleContext context) throws Exception {
    //Note that eclipse impose a time limitation on this method. The time consuming
    //sensor initialization code is moved to earlyStartup().
    super.start(context);
    SensorProperties sensorProperties = new SensorProperties(EclipseSensor.ECLIPSE);
    boolean isSensorEnabled = sensorProperties.isSensorEnabled();
    boolean isSensorUpdateEnabled 
        = sensorProperties.isSensorTypeEnabled(EclipseSensor.ECLIPSE_UPDATE);
    if (isSensorEnabled && isSensorUpdateEnabled) {
      SensorUpdateThread sensorUpdateThread = new SensorUpdateThread(context.getBundle());
      sensorUpdateThread.start();
    }       
  }
  
  /**
   * Implements an inner thread class to handle sensor update.  
   */
  private class SensorUpdateThread extends Thread {
    /** Resource bundle. */
    private Bundle bundle;
    
    /**
     * Hackystat sensor update thread.
     * 
     * @param bundle Plugin bundle.
     */
    SensorUpdateThread(Bundle bundle) {
      this.setName("SensorUpdateThread");
      this.bundle = bundle;
    }
    
    /**
     * Thread execution function.
     */
    public void run() {
      String title = EclipseSensorI18n.getString("VersionCheck.messageDialogTitle");
      String first = EclipseSensorI18n.getString("VersionCheck.messageDialogMessageFirst");
      String betweenKey = "VersionCheck.messageDialogMessageBetween";
      String between = EclipseSensorI18n.getString(betweenKey);
      String last = EclipseSensorI18n.getString("VersionCheck.messageDialogMessageLast");
      String messages[] = {first, between, last};
      
      EclipseSensor sensor = EclipseSensor.getInstance();
      String hackystathost = sensor.getHackystatHost();
      String updateURL = hackystathost + "hackystat/download/eclipse/site.xml";
      VersionCheck versionCheck = new VersionCheck(this.bundle); 
      versionCheck.processUpdateDialog(updateURL, title, messages);
    }
  }
  
  /**
   * Gets the path to the sensorshell.jar.
   * 
   * @return the path to the sensorshell.jar.
   */
  public String getSensorShellPath() {
    URL pluginUrl = super.getBundle().getEntry("/");
    try {      
      return FileLocator.toFileURL(new URL(pluginUrl, "sensorshell.jar")).getFile();
    }
    catch (Exception e) {
      return null;
    }
  }

  /**
   * Instantiates EclipseSensor class so that the collection for necessary data is ready. Note that
   * this is called when workbench starts up. This method must be overridden due to IStartup
   * interface. Because of this method, this class is instantiated on startup.
   *
   * @see IStartup
   */
  public void earlyStartup() {
    //To initialize the sensor.
    EclipseSensor.getInstance();
  }

  /**
   * Returns the shared instance.
   *
   * @return The this plug-in instance.
   */
  public static EclipseSensorPlugin getInstance() {
    return plugin;
  }
  
  /**
   * Returns the workspace instance. This method might be overridden due to AbstractUIPlugin
   * abstract class although it is not necessary to be overridden.
   *
   * @return The IWorkspace instance.
   *
   * @see AbstractUIPlugin
   */
  public static IWorkspace getWorkspace() {
    return ResourcesPlugin.getWorkspace();
  }


  /**
   * Logs out the exception or error message for Eclispe sensor plug-in.
   * 
   * @param e Exception. 
   */
  public void log(Exception e) {
    String pluginName = "org.hackystat.sensor.eclipse";
    if (super.getBundle() != null) {
      pluginName = super.getBundle().getSymbolicName();
    }
    
    IStatus status = new Status(IStatus.ERROR, pluginName, 0, e.getMessage(), e);
    plugin.getLog().log(status);
  }
}
