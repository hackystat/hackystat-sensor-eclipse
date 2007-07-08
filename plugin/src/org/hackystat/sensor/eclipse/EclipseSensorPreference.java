package org.hackystat.sensor.eclipse;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;

/**
 * Implements preference initializer since Eclipse 3.0.
 * 
 * @author Hongbing Kou
 * @version $Id: EclipseSensorPreference.java,v 1.1.1.1 2005/10/20 23:56:56 johnson Exp $
 */
public class EclipseSensorPreference extends AbstractPreferenceInitializer {

  /** 
   * Instantiate Eclipse plugin preference.
   */
  public EclipseSensorPreference() {
    super();
  }
  
  /**
   * Initializes preference for EclipseSensor plugin.
   */
  public void initializeDefaultPreferences() {
    //Preferences preference = EclipseSensorPlugin.getInstance().getPluginPreferences();
    EclipseSensorPlugin.getInstance().getPluginPreferences();
    
    //preference.setDefault(ENABLE_ECLIPSE_SENSOR, false);
    //preference.setDefault(PreferenceConstants.HACKYSTAT_KEY, "ChangeThisToYourPersonalKey");
    //preference.setDefault(PreferenceConstants.HACKYSTAT_HOST, "http://hackystat.ics.hawaii.edu/");
    //preference.setDefault(PreferenceConstants.HACKYSTAT_AUTOSEND_INTERVAL, "10");
    //preference.setDefault(PreferenceConstants.HACKYSTAT_STATE_CHANGE_INTERVAL, "30");
    //preference.setDefault(PreferenceConstants.ECLIPSE_UPDATE_URL, 
    //        "http://hackystat.ics.hawaii.edu/hackystat/download/eclipse/site.xml");
    //preference.setDefault(PreferenceConstants.HACKYSTAT_BUFFTRANS_INTERVAL, "2");
    //preference.setDefault(PreferenceConstants.ENABLE_ECLIPSE_MONITOR_SENSOR, true);
    //preference.setDefault(PreferenceConstants.ENABLE_ECLIPSE_UPDATE_SENSOR, true); 
    //preference.setDefault(PreferenceConstants.ENABLE_ECLIPSE_BUFFTRANS_SENSOR, false);  
	}
}
