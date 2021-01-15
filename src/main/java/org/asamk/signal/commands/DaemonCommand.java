package org.asamk.signal.commands;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.asamk.signal.DbusReceiveMessageHandler;
import org.asamk.signal.JsonDbusReceiveMessageHandler;
import org.asamk.signal.dbus.DbusSignalImpl;
import org.asamk.signal.manager.Manager;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.asamk.signal.DbusConfig.SIGNAL_BUSNAME;
import static org.asamk.signal.DbusConfig.SIGNAL_OBJECTPATH;
import static org.asamk.signal.util.ErrorUtils.handleAssertionError;

public class DaemonCommand implements LocalCommand {

    private final static Logger logger = LoggerFactory.getLogger(ReceiveCommand.class);

    @Override
    public void attachToSubparser(final Subparser subparser) {
        subparser.addArgument("--system")
                .action(Arguments.storeTrue())
                .help("Use DBus system bus instead of user bus.");
        subparser.addArgument("--ignore-attachments")
                .help("Don’t download attachments of received messages.")
                .action(Arguments.storeTrue());
        subparser.addArgument("--json")
                .help("WARNING: This parameter is now deprecated! Please use the global \"--output=json\" option instead.\n\nOutput received messages in json format, one json object per line.")
                .action(Arguments.storeTrue());
    }

    @Override
    public int handleCommand(final Namespace ns, final Manager m) {
        boolean inJson = ns.getString("output").equals("json") || ns.getBoolean("json");

        // TODO delete later when "json" variable is removed
        if (ns.getBoolean("json")) {
            logger.warn("\"--json\" option has been deprecated, please use the global \"--output=json\" instead.");
        }

        DBusConnection conn = null;
        try {
            try {
                DBusConnection.DBusBusType busType;
                if (ns.getBoolean("system")) {
                    busType = DBusConnection.DBusBusType.SYSTEM;
                } else {
                    busType = DBusConnection.DBusBusType.SESSION;
                }
                conn = DBusConnection.getConnection(busType);
                conn.exportObject(SIGNAL_OBJECTPATH, new DbusSignalImpl(m));
                conn.requestBusName(SIGNAL_BUSNAME);
            } catch (UnsatisfiedLinkError e) {
                System.err.println("Missing native library dependency for dbus service: " + e.getMessage());
                return 1;
            } catch (DBusException e) {
                e.printStackTrace();
                return 2;
            }
            boolean ignoreAttachments = ns.getBoolean("ignore_attachments");
            try {
                m.receiveMessages(1,
                        TimeUnit.HOURS,
                        false,
                        ignoreAttachments,
                        inJson
                                ? new JsonDbusReceiveMessageHandler(m, conn, SIGNAL_OBJECTPATH)
                                : new DbusReceiveMessageHandler(m, conn, SIGNAL_OBJECTPATH));
                return 0;
            } catch (IOException e) {
                System.err.println("Error while receiving messages: " + e.getMessage());
                return 3;
            } catch (AssertionError e) {
                handleAssertionError(e);
                return 1;
            }
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
