package de.uni_passau.fim.auermich.cli.jcommander;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;
import de.uni_passau.fim.auermich.core.graphs.GraphType;

import java.util.Optional;

/**
 * Converts a graph type to it's internal representation.
 */
public class GraphTypeConverter implements IStringConverter<GraphType> {

    /**
     * Converts a string defining a graph type to its equivalent enum representation.
     *
     * @param value The input defining a graph type, e.g. intra-cfg.
     * @return Returns the enum representation of the given graph type.
     */
    @Override
    public GraphType convert(String value) {
        Optional<GraphType> graphType = GraphType.fromString(value);

        if (!graphType.isPresent()) {
            throw new ParameterException("Value " + value + " is not a valid graph type.");
        } else {
            return graphType.get();
        }
    }
}
