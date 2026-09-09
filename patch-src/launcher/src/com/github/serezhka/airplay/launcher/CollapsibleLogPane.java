package com.github.serezhka.airplay.launcher;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;

final class CollapsibleLogPane extends JPanel {
    private final JComponent configuration;
    private final JComponent logs;
    private final JSplitPane splitPane;
    private boolean expanded;
    private int dividerLocation = 440;

    CollapsibleLogPane(JComponent configuration, JComponent logs) {
        super(new BorderLayout());
        this.configuration = configuration;
        this.logs = logs;
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, configuration, logs);
        splitPane.setResizeWeight(0.40);
        splitPane.setBorder(null);
        setExpanded(false);
    }

    boolean isExpanded() {
        return expanded;
    }

    void setExpanded(boolean expanded) {
        if (this.expanded == expanded && getComponentCount() > 0) {
            return;
        }
        if (this.expanded) {
            dividerLocation = splitPane.getDividerLocation();
        }
        this.expanded = expanded;
        removeAll();
        logs.setVisible(expanded);
        if (expanded) {
            splitPane.setLeftComponent(configuration);
            splitPane.setRightComponent(logs);
            add(splitPane, BorderLayout.CENTER);
            splitPane.setDividerLocation(boundedDividerLocation(dividerLocation));
        } else {
            splitPane.setLeftComponent(null);
            splitPane.setRightComponent(null);
            add(configuration, BorderLayout.CENTER);
        }
        revalidate();
        repaint();
    }

    @Override
    public void doLayout() {
        super.doLayout();
        if (expanded) {
            int current = splitPane.getDividerLocation();
            int bounded = boundedDividerLocation(current);
            if (current != bounded) {
                splitPane.setDividerLocation(bounded);
            }
        }
    }

    private int boundedDividerLocation(int location) {
        int minimum = configuration.getMinimumSize().width;
        if (getWidth() == 0) {
            return Math.max(location, minimum);
        }
        int maximum = Math.max(minimum, getWidth() - splitPane.getDividerSize() - logs.getMinimumSize().width);
        return Math.max(minimum, Math.min(location, maximum));
    }
}
