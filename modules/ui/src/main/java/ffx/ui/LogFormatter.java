/**
 * Title: Force Field X
 * Description: Force Field X - Software for Molecular Biophysics.
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2009
 *
 * This file is part of Force Field X.
 *
 * Force Field X is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as published
 * by the Free Software Foundation.
 *
 * Force Field X is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Force Field X; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */
package ffx.ui;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * A minor extension to the SimpleFormatter to reduce verbosity
 * if debugging is not turned on.
 *
 * @author Michael J. Schnieders
 * @since 1.0
 */
public class LogFormatter extends SimpleFormatter {

    private final boolean debug;
    private static final int warningLevel = Level.WARNING.intValue();

    /**
     * Constructor for the LogFormatter.
     * @param debug If debug is true, then LogFormatter is equivalent to
     *      {@link SimpleFormatter}.
     * @since 1.0
     */
    public LogFormatter(boolean debug) {
        this.debug = debug;
    }

    /**
     * Unless debugging is turned on or the LogRecord is of level WARNING or
     * greater, just return the message.
     *
     * @param record The LogRecord to format.
     * @return A formatted string.
     * @since 1.0
     */
    @Override
    public String format(LogRecord record) {
        if (debug || record.getLevel().intValue() >= warningLevel) {
            return super.format(record);
        }
        return record.getMessage();
    }
}
