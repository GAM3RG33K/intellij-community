// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.jcef.JBCefBrowser;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * @author tav
 */
public class WebBrowser extends AnAction implements DumbAware {
  private static final String URL = "http://maps.google.com";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Window activeFrame = IdeFrameImpl.getActiveFrame();
    if (activeFrame == null) return;

    if (!Registry.is("ide.browser.jcef.enabled")) {
      JBPopupFactory.getInstance().createComponentPopupBuilder(
        new JTextArea("Set the reg key to enable JCEF:\n\"ide.browser.jcef.enabled=true\""), null).
        setTitle("JCEF web browser is not available").
        createPopup().
        showInCenterOf(activeFrame);
      return;
    }

    Rectangle bounds = activeFrame.getGraphicsConfiguration().getBounds();

    JFrame frame = new IdeFrameImpl();
    frame.setTitle("Web Browser - JCEF");
    frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    frame.setBounds(bounds.width / 4, bounds.height / 4, bounds.width / 2, bounds.height / 2);
    frame.setLayout(new BorderLayout());

    JBCefBrowser browser = new JBCefBrowser(URL);
    frame.add(browser.getComponent(), BorderLayout.CENTER);
    frame.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosed(WindowEvent e) {
        Disposer.dispose(browser);
      }
    });

    JMenuItem item = new JMenuItem("Show Dev Tools");
    item.addActionListener((event) -> {
      JBCefBrowser devTools = new JBCefBrowser(browser.getCefBrowser().getDevTools());
      JDialog dialog = new JDialog(frame, "JCEF Dev Tools");
      dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      dialog.setBounds(bounds.width / 4 + 100, bounds.height / 4 + 100, bounds.width / 2, bounds.height / 2);
      dialog.add(devTools.getComponent());
      dialog.setVisible(true);
    });

    JMenu menu = new JMenu("Tools");
    menu.add(item);
    frame.setJMenuBar(new JMenuBar());
    frame.getJMenuBar().add(menu);

    JTextField urlBar = new JTextField(URL);
    urlBar.addActionListener(event -> browser.loadURL(urlBar.getText()));
    frame.add(urlBar, BorderLayout.NORTH);
    frame.setVisible(true);
  }
}
