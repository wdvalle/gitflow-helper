package br.com.gitflowhelper.util;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

@Deprecated
public class PropertyObserver {
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    @Deprecated
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    @Deprecated
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    @Deprecated
    public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        pcs.firePropertyChange(propertyName, oldValue, newValue);
    }
}