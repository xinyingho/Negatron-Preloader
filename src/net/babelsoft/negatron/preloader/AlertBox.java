/*
 * This file is part of Negatron.
 * Copyright (C) 2015-2018 BabelSoft S.A.S.U.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.babelsoft.negatron.preloader;

import java.awt.*;
import java.awt.event.*;

/**
 *
 * @author capan
 */
public final class AlertBox extends Dialog implements ActionListener {
    
    private Button okButton;
    private Button cancelButton;
    public boolean isOk = false;

    /*
     * @param frame             parent frame 
     * @param msg               message to be displayed
     * @param addCancelButton   true: display both ok and cancel buttons, false: ok button only 
     */
    AlertBox(Frame frame, String msg, boolean addCancelButton){
        super(frame, Language.Manager.getString("fatalError"), true);
        setLayout(new BorderLayout());
        add("Center", new Label(msg));
        addOKCancelPanel(addCancelButton);
        createFrame();
        pack();
        setVisible(true);
    }
    
    AlertBox(Frame frame, String msg){
        this(frame, msg, false);
    }
    
    void addOKCancelPanel(boolean addCancelButton) {
        Panel p = new Panel();
        p.setLayout(new FlowLayout());
        createOkButton(p);
        if (addCancelButton)
            createCancelButton(p);
        add("South", p);
    }

    void createOkButton(Panel p) {
        p.add(okButton = new Button(Language.Manager.getString("ok")));
        okButton.addActionListener(this); 
    }

    void createCancelButton(Panel p) {
        p.add(cancelButton = new Button(Language.Manager.getString("cancel")));
        cancelButton.addActionListener(this);
    }

    void createFrame() {
        Dimension d = getToolkit().getScreenSize();
        setLocation(d.width / 3, d.height / 3);
    }

    @Override
    public void actionPerformed(ActionEvent ae){
        if(ae.getSource() == okButton) {
            isOk = true;
            setVisible(false);
        }
        else if(ae.getSource() == cancelButton) {
            setVisible(false);
        }
    }
    
    public static AlertBox showAndWait(String errorMessage) {
        return new AlertBox(null, errorMessage, false);
    }
}