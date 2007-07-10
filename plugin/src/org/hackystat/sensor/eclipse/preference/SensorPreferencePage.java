package org.hackystat.sensor.eclipse.preference;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.hackystat.sensor.eclipse.EclipseSensorPlugin;

/**
 * Implements preference initializer since Eclipse 3.0.
 * 
 * @author Hongbing Kou
 * @version $Id: EclipseSensorPreference.java,v 1.1.1.1 2005/10/20 23:56:56 johnson Exp $
 */
public class SensorPreferencePage extends FieldEditorPreferencePage 
    implements IWorkbenchPreferencePage {
  /** 
   * Instantiate Eclipse plugin preference.
   */
  public SensorPreferencePage() {
    super(GRID);
    
    EclipseSensorPlugin plugin = EclipseSensorPlugin.getDefault();
    setPreferenceStore(plugin.getPreferenceStore());
  }
  
  /** Creates the field editors. Field editors are abstractions of
   * the common GUI blocks needed to manipulate various types
   * of preferences. Each field editor knows how to save and
   * restore itself.
   */
  public void createFieldEditors() {
    // hackystat host
    StringFieldEditor hostField = new StringFieldEditor(
        PreferenceConstants.P_HOST, "&Host", getFieldEditorParent());
    hostField.setEmptyStringAllowed(false);
    super.addField(hostField);

    // user key
    StringFieldEditor userKeyField = new StringFieldEditor(
        PreferenceConstants.P_USERKEY, "User &Key", getFieldEditorParent());
    userKeyField.setEmptyStringAllowed(false);
    super.addField(userKeyField);

    // Enable/disable sensor
    BooleanFieldEditor enableEditor = new BooleanFieldEditor(
        PreferenceConstants.P_ENABLE, "&Enable Eclipse Sensor", getFieldEditorParent());
    super.addField(enableEditor);

    // Monitoring message that appears on status bar
    BooleanFieldEditor monitorEditor = new BooleanFieldEditor(
        PreferenceConstants.P_ENABLE_MONITOR,
        "Enable &Monitor", getFieldEditorParent());
    super.addField(monitorEditor);
    
    // Whether or not allow auto update
    BooleanFieldEditor autoUpdateEditor = new BooleanFieldEditor(
        PreferenceConstants.P_ENABLE_AUTOUPDATE,
        "Enable &AutoUpdate", getFieldEditorParent());
    super.addField(autoUpdateEditor);
    
    // Interval to send data over to a centralized server.
    //IntegerFieldEditor sendIntervalEditor = new IntegerFieldEditor(
    //    PreferenceConstants.P_AUTOSEND_INTERVAL,
    //    "Autosend &Interval (Minutes)", getFieldEditorParent());
    //sendIntervalEditor.setEmptyStringAllowed(false);
    //super.addField(sendIntervalEditor);
    
    // State change interval. 
    //IntegerFieldEditor stateChangeIntervalEditor = new IntegerFieldEditor(
    //    PreferenceConstants.P_STATECHANGE_INTERVAL,
    //    "&Statechange Interval (Seconds)", getFieldEditorParent());
    //super.addField(stateChangeIntervalEditor);
    
    
    //IntegerFieldEditor bufferTransIntervalEditor = new IntegerFieldEditor(
    //    PreferenceConstants.P_BUFFTRANS_INTERVAL,
    //    "&BufferTrans Interval", getFieldEditorParent());
    //addField(bufferTransIntervalEditor);

    
    StringFieldEditor udpateSiteField = new StringFieldEditor(
        PreferenceConstants.P_UPDATE_SITE, "&Update Site", getFieldEditorParent());
    super.addField(udpateSiteField);
    
    //BooleanFieldEditor bufferTransEditor = new BooleanFieldEditor(
    //    PreferenceConstants.P_ENABLE_BUFFTRANS,
    //    "Enable &BufferTrans", getFieldEditorParent());
    //super.addField(bufferTransEditor);
  }

  /**
   * Initialize the preference page. 
   * 
   * @param workbench Eclipse workbench.
   */
  public void init(IWorkbench workbench) {
    //Initialize the preference store we wish to use
    setPreferenceStore(EclipseSensorPlugin.getDefault().getPreferenceStore());
  }
  
  /**
   * Perform okay to validate host/userkey.
   */
  protected void performApply() {
    super.performApply();
    
    // Validate Hackystat host.
    // ...
  }
}