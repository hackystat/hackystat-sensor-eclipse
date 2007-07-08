package org.hackystat.sensor.eclipse;

import java.util.TimerTask;

/**
 * Implements Eclipse sensor timer task that can be executed by a timer. The timer task checks
 * buffer transition, which represents the event where a developer moves from one buffer
 * (containing a file) to another buffer (containing a different file).
 *
 * @author Takuya Yamashita
 * @version $Id: BuffTransTimertask.java,v 1.1.1.1 2005/10/20 23:56:56 johnson Exp $
 */
public class BuffTransTimertask extends TimerTask {
  /**
   * Processes the state change activity and computes file metrics in a time based interval.
   */
  public void run() {
    EclipseSensor sensor = EclipseSensor.getInstance();
    // process buffer transactions.
    sensor.processBuffTrans();
  }
}
