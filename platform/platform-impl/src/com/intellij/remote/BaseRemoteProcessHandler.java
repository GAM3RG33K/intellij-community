// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remote;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.process.BaseProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.Future;

public class BaseRemoteProcessHandler<T extends RemoteProcess> extends BaseProcessHandler<T> {
  private static final Logger LOG = Logger.getInstance(BaseRemoteProcessHandler.class);

  public BaseRemoteProcessHandler(@NotNull T process, /*@NotNull*/ String commandLine, @Nullable Charset charset) {
    super(process, commandLine, charset);
  }

  /**
   * Override this method to fine-tune {@link BaseOutputReader} behavior.
   */
  @NotNull
  protected BaseOutputReader.Options readerOptions() {
    return new BaseOutputReader.Options();
  }


  @Override
  protected void destroyProcessImpl() {
    if (!myProcess.killProcessTree()) {
      super.destroyProcessImpl();
    }
  }

  @Override
  public void startNotify() {
    notifyTextAvailable(myCommandLine + '\n', ProcessOutputTypes.SYSTEM);

    addProcessListener(new ProcessAdapter() {
      @Override
      public void startNotified(@NotNull final ProcessEvent event) {
        try {
          final RemoteOutputReader stdoutReader = new RemoteOutputReader(myProcess.getInputStream(), getCharset(), myProcess, myCommandLine, readerOptions()) {
            @Override
            protected void onTextAvailable(@NotNull String text) {
              notifyTextAvailable(text, ProcessOutputTypes.STDOUT);
            }

            @NotNull
            @Override
            protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
              return BaseRemoteProcessHandler.this.executeTask(runnable);
            }
          };

          final RemoteOutputReader stderrReader = new RemoteOutputReader(myProcess.getErrorStream(), getCharset(), myProcess, myCommandLine, readerOptions()) {
            @Override
            protected void onTextAvailable(@NotNull String text) {
              notifyTextAvailable(text, ProcessOutputTypes.STDERR);
            }

            @NotNull
            @Override
            protected Future<?> executeOnPooledThread(@NotNull Runnable runnable) {
              return BaseRemoteProcessHandler.this.executeTask(runnable);
            }
          };

          myWaitFor.setTerminationCallback(exitCode -> {
            try {
              try {
                stderrReader.waitFor();
                stdoutReader.waitFor();
              }
              catch (InterruptedException ignore) { }
            }
            finally {
              onOSProcessTerminated(exitCode);
            }
          });
        }
        finally {
          removeProcessListener(this);
        }
      }
    });

    super.startNotify();
  }

  @NotNull
  @Override
  public Future<?> executeTask(@NotNull Runnable task) {
    return ApplicationManager.getApplication().executeOnPooledThread(task);
  }

  private abstract static class RemoteOutputReader extends BaseOutputReader {
    @NotNull private final RemoteProcess myRemoteProcess;
    private boolean myClosed;

    RemoteOutputReader(@NotNull InputStream inputStream,
                       Charset charset,
                       @NotNull RemoteProcess remoteProcess,
                       @NotNull String commandLine,
                       @NotNull Options options) {
      super(inputStream, charset, options);

      myRemoteProcess = remoteProcess;

      start(CommandLineUtil.extractPresentableName(commandLine));
    }

    @Override
    protected void doRun() {

      try {
        setClosed(false);
        while (true) {
          final boolean read = readAvailable();

          if (myRemoteProcess.isDisconnected()) {
            myReader.close();
            break;
          }

          if (isStopped) {
            break;
          }

          Thread.sleep(mySleepingPolicy.getTimeToSleep(read)); // give other threads a chance
        }
      }
      catch (InterruptedException ignore) {
      }
      catch (Exception e) {
        LOG.warn(e);
      }
      finally {
        setClosed(true);
      }
    }

    protected synchronized void setClosed(boolean closed) {
      myClosed = closed;
    }

    @Override
    public void waitFor() throws InterruptedException {
      while (!isClosed()) {
        Thread.sleep(100);
      }
    }

    private synchronized boolean isClosed() {
      return myClosed;
    }
  }
}