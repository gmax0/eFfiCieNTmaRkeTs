package domain.control;

import static domain.control.ControlCommand.SILENT;

/** CommandMonitor is used for synchronizing threads that receive and send ControlCommands */
public class CommandMonitor {

  private Object monitor = new Object();
  private ControlCommand.Command command = SILENT;

  /**
   * Set a command that awaiting threads will receive
   * @param command the command that awaiting threads will receive and return through the awaitCommand() method
   */
  public void submitCommand(ControlCommand.Command command) {
    synchronized (monitor) {
      this.command = command;
      monitor.notify();
    }
  }

  /**
   * Waits and returns the command set by a notifying thread.
   * @return The command sent by a notifying thread.
   * @throws InterruptedException
   */
  public ControlCommand.Command awaitCommand() throws InterruptedException {
    synchronized (monitor) {
      if (this.command == SILENT) {
        monitor.wait();
      }

      ControlCommand.Command command = this.command;
      this.command = SILENT;
      return command;
    }
  }
}
