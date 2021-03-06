/**
 *
 */
package roslab.model.electronics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import roslab.model.general.Endpoint;
import roslab.model.general.Feature;
import roslab.model.general.Link;
import roslab.model.general.Node;
import roslab.model.ui.UIEndpoint;
import roslab.processors.electronics.EagleSchematic;
import roslab.processors.electronics.PinMatcher;

/**
 * @author Peter Gebhard
 */
public class Circuit extends Node implements Endpoint {

    Circuit spec;
    EagleSchematic schematic;

    List<WireBundle> wireBundles = new ArrayList<WireBundle>();

    // TODO CircuitType type;
    // TODO List<CircuitResource> resources;

    /**
     * @param name
     * @param spec
     */
    public Circuit(String name, Circuit spec) {
        super(name, new HashMap<String, Pin>(), spec.getAnnotationsCopy());
        this.spec = spec;
        this.schematic = spec.schematic.clone();
        this.features = spec.getPinsCopy(this);
    }

    /**
     * @param name
     * @param pins
     * @param annotations
     * @param spec
     */
    public Circuit(String name, Map<String, Pin> pins) {
        super(name, pins, new HashMap<String, String>());
    }

    /**
     * @param name
     */
    public Circuit(String name) {
        super(name, new HashMap<String, Pin>(), new HashMap<String, String>());
    }

    @Override
    public Circuit getSpec() {
        return spec;
    }

    /**
     * @return the schematic
     */
    public EagleSchematic getSchematic() {
        return schematic;
    }

    /**
     * @param schematic
     *            the schematic to set
     */
    public void setSchematic(EagleSchematic schematic) {
        this.schematic = schematic;
    }

    @SuppressWarnings("unchecked")
    public void addPin(Pin p) {
        ((Map<String, Pin>) features).put(p.getName(), p);
    }

    public Pin getPin(String name) {
        return (Pin) features.get(name);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Pin> getPins() {
        return (Map<String, Pin>) features;
    }

    /**
     * Get the circuit's required pins.
     *
     * @return A map of pin names to Pin objects (only the required pins)
     */
    public Map<String, Pin> getRequiredPins() {
        Map<String, Pin> result = new HashMap<String, Pin>();

        for (Pin p : this.getPins().values()) {
            if (p.required) {
                result.put(p.getName(), p);
            }
        }

        return result;
    }

    /**
     * Check if the pin is required and unconnected
     *
     * @return A map of pin names to Pin objects (only the unconnected required
     *         pins)
     */
    public Map<String, Pin> getUnconnectedRequiredPins() {
        Map<String, Pin> result = new HashMap<String, Pin>();

        for (Pin p : this.getPins().values()) {
            if (p.required && !((Circuit) p.getParent()).isPinConnected(p)) {
                result.put(p.getName(), p);
            }
        }

        return result;
    }

    /**
     * Check if the pin is required, but also check if the pin is either
     * unconnected or it is a bussable connection
     *
     * @return A map of pin names to Pin objects (only the unconnected required
     *         pins)
     */
    public Map<String, Pin> getUnconnectedOrBussableRequiredPins() {
        Map<String, Pin> result = new HashMap<String, Pin>();

        for (Pin p : this.getPins().values()) {
            if (p.required && (!((Circuit) p.getParent()).isPinConnected(p) || (p.assignedService != null && p.assignedService.one_to_many == '+'))) {
                result.put(p.getName(), p);
            }
        }

        return result;
    }

    public Map<String, Pin> getPinsCopy(Circuit c) {
        Map<String, Pin> copy = new HashMap<String, Pin>();
        for (Entry<String, ? extends Feature> e : features.entrySet()) {
            copy.put(e.getKey(), ((Pin) e.getValue()).clone(e.getKey(), c));
        }
        return copy;
    }

    public Pin getUnusedServiceByName(String serviceName) {
        for (Entry<String, ? extends Feature> entry : features.entrySet()) {
            Pin pin = (Pin) entry.getValue();

            // If the pin has not been assigned yet...
            if (pin.getAssignedService() == null) {
                // Look through all the pin's services to see if it has a
                // matching service
                for (PinService service : pin.getServices()) {
                    if (service.getName().equals(serviceName)) {
                        return pin;
                    }
                }
            }
        }

        // TODO: Need to add optimizing algorithm here to handle cases where
        // there is initially no requested service unused, but through some
        // reassignment of pins, we could make it work.

        return null;
    }

    /*
     * (non-Javadoc)
     * @see roslab.model.general.Endpoint#isFanIn()
     */
    @Override
    public boolean isFanIn() {
        return true;
    }

    /*
     * (non-Javadoc)
     * @see roslab.model.general.Endpoint#isFanOut()
     */
    @Override
    public boolean isFanOut() {
        return true;
    }

    /*
     * (non-Javadoc)
     * @see
     * roslab.model.general.Endpoint#canConnect(roslab.model.general.Endpoint)
     */
    @Override
    public boolean canConnect(Endpoint e) {
        if (e instanceof Circuit && this.getUnconnectedOrBussableRequiredPins().size() > 0) {
            Circuit c = (Circuit) e;
            Map<Integer, Integer> mapping = new HashMap<Integer, Integer>();
            List<Pin> componentPins = c.getConnectedComponentPins();
            componentPins.addAll(this.getUnconnectedOrBussableRequiredPins().values());

            // Fill the pin matching matrix
            Integer[][] matrix = new Integer[componentPins.size()][c.getPins().size()];
            int mIndex = 0;
            for (Pin p : componentPins) {
                matrix[mIndex] = generateRow(c, p);
                mIndex++;
            }

            mapping = PinMatcher.match(matrix, Pin.toPinArray(componentPins), Pin.toPinArray(new ArrayList<Pin>(c.getPins().values())));

            if (mapping == null) {
                return false;
            }

            // Check if any new connections were made (to connect specifically
            // to this component)
            int cConnectedPinCount = c.getConnectedComponentPins().size();
            boolean containsNewPinConnection = false;
            for (Integer i : mapping.keySet()) {
                if (i >= cConnectedPinCount) {
                    containsNewPinConnection = true;
                }
            }

            return containsNewPinConnection;
        }

        return false;
    }

    private List<Pin> getConnectedComponentPins() {
        List<Pin> result = new ArrayList<Pin>();

        for (WireBundle wb : this.wireBundles) {
            for (Wire w : wb.wires) {
                if (this.equals(w.src.getParent())) {
                    result.add(w.dest);
                }
                else {
                    result.add(w.src);
                }
            }
        }

        return result;
    }

    @Override
    public Link connect(Endpoint e) {
        if (e instanceof Circuit) {
            Circuit c = (Circuit) e;

            if (canConnect(c)) {
                Map<Integer, Integer> mapping = new HashMap<Integer, Integer>();
                List<Pin> componentPins = c.getConnectedComponentPins();
                componentPins.addAll(this.getUnconnectedOrBussableRequiredPins().values());

                // Fill the pin matching matrix
                Integer[][] matrix = new Integer[componentPins.size()][c.getPins().size()];
                int mIndex = 0;
                for (Pin p : componentPins) {
                    matrix[mIndex] = generateRow(c, p);
                    mIndex++;
                }

                mapping = PinMatcher.match(matrix, Pin.toPinArray(componentPins), Pin.toPinArray(new ArrayList<Pin>(c.getPins().values())));

                // Return false if the match did not work, ie. the component
                // cannot be connected.
                if (mapping == null || mapping.isEmpty()) {
                    return null;
                }

                WireBundle wb = new WireBundle(this, c);

                // TODO Doesn't handle pin removing and reattaching!

                for (Entry<Integer, Integer> pinPair : mapping.entrySet()) {
                    Wire w = ((Pin) componentPins.toArray()[pinPair.getKey()]).connect((Pin) c.getPins().values().toArray()[pinPair.getValue()]);
                    // TODO Check each wire and see if the source pin has either
                    // an existing wire, and if so, check if the destination pin
                    // is different than the existing wire's destination, if so,
                    // delete it from its wire bundle and add this new wire to
                    // that existing wire bundle
                    if (w != null && w.src.getParent().equals(this)) {
                        wb.addWire(w);
                    }
                }

                // Add this WireBundle to the list of WireBundles in this
                // circuit and the destination circuit
                wireBundles.add(wb);
                c.wireBundles.add(wb);

                return wb;
            }

            // Try connecting in the other direction if the original direction
            // doesn't work
            else if (c.canConnect(this)) {
                c.connect(this);
            }
        }

        return null;
    }

    public boolean isPinConnected(Pin p) {
        for (WireBundle wb : wireBundles) {
            for (Wire w : wb.wires) {
                if (w.src.equals(p) || w.dest.equals(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void disconnect(Link l) {
        ((Circuit) l.getSrc()).wireBundles.remove(l);
        ((Circuit) l.getDest()).wireBundles.remove(l);
        // TODO Remove wire bundle connections from EagleSchematic netMap
    }

    @Override
    public List<WireBundle> getLinks() {
        return wireBundles;
    }

    @Override
    public Node getParent() {
        return spec;
    }

    @Override
    public List<Endpoint> getEndpoints() {
        ArrayList<Endpoint> list = new ArrayList<Endpoint>();
        list.add(this);
        return list;
    }

    @Override
    public UIEndpoint getUIEndpoint() {
        return uiNode.getUIEndpoint(this);
    }

    @Override
    public boolean isInput() {
        return true;
    }

    @Override
    public Circuit clone(String name) {
        return new Circuit(name, this);
    }

    private Integer[] generateRow(Circuit c, Pin p) {
        Integer[] result = new Integer[c.getPins().size()];
        int i = 0;

        for (Pin pin : c.getPins().values()) {
            if (p.canConnect(pin)) {
                result[i] = 1;
            }
            else {
                result[i] = 0;
            }
            i++;
        }

        return result;
    }
}
