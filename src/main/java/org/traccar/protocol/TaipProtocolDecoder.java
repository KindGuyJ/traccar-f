/*
 * Copyright 2013 - 2026 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import io.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.session.DeviceSession;
import org.traccar.NetworkMessage;
import org.traccar.Protocol;
import org.traccar.helper.BitUtil;
import org.traccar.helper.Checksum;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.DateUtil;
import org.traccar.helper.Parser;
import org.traccar.helper.PatternBuilder;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Position;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class TaipProtocolDecoder extends BaseProtocolDecoder {

    public TaipProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    private static final Pattern PATTERN = new PatternBuilder()
            .groupBegin()
            .expression("R[EP]V")                // type
            .groupBegin()
            .number("(dd)")                      // event
            .number("(dddd)")                    // week
            .number("(d)")                       // day
            .groupEnd("?")
            .number("(d{5})")                    // seconds
            .or()
            .expression("(?:RGP|RCQ|RCV|RBR|RUS00|RPI|RUV[0-9]{2}),?") // type
            .number("(d+)?")                     // event
            .expression(",?")
            .expression("(?:[A-Z][A-Z0-9]*,)?")  // protocol
            .number("(dd)(dd)(dd)")              // date (ddmmyy)
            .number("(dd)(dd)(dd)")              // time (hhmmss)
            .groupEnd()
            .groupBegin()
            .groupBegin()
            .number("([-+]dd)(d{5})")            // latitude
            .number("([-+]ddd)(d{5})")           // longitude
            .or()
            .number("([-+])(dd)(dd.dddd)")       // latitude
            .number("([-+])(ddd)(dd.dddd)")      // longitude
            .groupEnd()
            .number("(ddd)")                     // speed
            .number("(ddd)")                     // course
            .groupBegin()
            .number("(d)")                       // fix mode
            .number("xx")                        // data age
            .number("(xx)")                      // input
            .groupBegin()
            .number(",d+")                       // flow meter
            .number(",(d+)")                     // odometer
            .number(",(d{4})(d{4})")             // power / battery
            .number(",(d+)")                     // rpm
            .groupBegin()
            .number(",([-+]?d+.?d*)")            // temperature 1
            .number(",([-+]?d+.?d*)")            // temperature 2
            .groupEnd("?")
            .number(",(xx)")                     // alarm
            .or()
            .number("(dd)")                      // event
            .number("(dd)")                      // hdop
            .groupEnd()
            .or()
            .groupBegin()
            .number("(xx)")                      // input
            .number("xx")                        // satellites / outputs
            .number("(ddd)")                     // battery
            .number("(x{8})")                    // odometer
            .number("[01]")                      // gps power
            .groupBegin()
            .number("([023])")                   // fix mode
            .number("(dd)")                      // pdop
            .number("dd")                        // satellites
            .number("xxxx")                      // data age
            .number("[01]")                      // modem power
            .number("[0-5]")                     // gsm status
            .number("(dd)")                      // rssi
            .groupBegin()
            .number("([-+]dddd)")                // temperature 1
            .number("xx")                        // seconds from last
            .number("([-+]dddd)")                // temperature 2
            .number("xx")                        // seconds from last
            .groupEnd("?")
            .groupEnd("?")
            .groupEnd("?")
            .groupEnd()
            .groupEnd("?")
            .any()
            .compile();

    private static final Pattern PATTERN_RINHO = new PatternBuilder()
            .expression("R(CQ|EQ|ER)")           // type
            .number("(xx)")                      // report id
            .number("(dd)(dd)(dd)")              // date (ddmmyy)
            .number("(dd)(dd)(dd)")              // time (hhmmss)
            .number("([-+]dd)(d{5})")            // latitude
            .number("([-+]ddd)(d{5})")           // longitude
            .number("(ddd)")                     // speed (km/h)
            .number("(ddd)")                     // course
            .number("(xx)")                      // input
            .number("(xx)")                      // output
            .number("(ddd)")                     // power
            .number("(x{8})")                    // odometer
            .number("([01])")                    // gps power
            .number("(d)")                       // fix mode
            .number("(dd)")                      // pdop
            .number("(dd)")                      // satellites
            .number("(xxxx)")                    // data age
            .number("[01]")                      // modem power
            .number("d")                         // gsm status
            .number("(dd)")                      // rssi
            .any()
            .compile();

    private Date getTime(long week, long day, long seconds) {
        DateBuilder dateBuilder = new DateBuilder()
                .setDate(1980, 1, 6)
                .addMillis(((week * 7 + day) * 24 * 60 * 60 + seconds) * 1000);
        return dateBuilder.getDate();
    }

    private Date getTime(long seconds) {
        DateBuilder dateBuilder = new DateBuilder(new Date())
                .setTime(0, 0, 0, 0)
                .addMillis(seconds * 1000);
        return DateUtil.correctDay(dateBuilder.getDate());
    }

    private String decodeAlarm(int value) {
        return switch (value) {
            case 0x01 -> Position.ALARM_SOS;
            case 0x02 -> Position.ALARM_POWER_CUT;
            default -> null;
        };
    }

    private String decodeAlarm2(int value) {
        return switch (value) {
            case 22 -> Position.ALARM_ACCELERATION;
            case 23 -> Position.ALARM_BRAKING;
            case 24 -> Position.ALARM_ACCIDENT;
            case 26, 28 -> Position.ALARM_CORNERING;
            default -> null;
        };
    }

    private String decodeAlarmAkroz(int event) {
        return switch (event) {
            case 104, 106 -> Position.ALARM_OVERSPEED;
            case 109 -> Position.ALARM_HIGH_RPM;
            case 111 -> Position.ALARM_IDLE;
            case 119 -> Position.ALARM_TEMPERATURE;
            case 120 -> Position.ALARM_ACCELERATION;
            case 121 -> Position.ALARM_BRAKING;
            case 122 -> Position.ALARM_CORNERING;
            case 127 -> Position.ALARM_POWER_CUT;
            case 128 -> Position.ALARM_POWER_RESTORED;
            case 129 -> Position.ALARM_LOW_BATTERY;
            default -> null;
        };
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        String sentence = (String) msg;

        int beginIndex = sentence.indexOf('>');
        if (beginIndex != -1) {
            sentence = sentence.substring(beginIndex + 1);
        }

        if (sentence.startsWith("KA;")) {
            // keep-alive carries no position and no ACK, but identifies the device on new connections
            for (String attribute : splitAttributes(sentence)) {
                if (attribute.startsWith("ID=")) {
                    getDeviceSession(channel, remoteAddress, attribute.substring(3));
                }
            }
            return null;
        }

        Parser rinhoParser = new Parser(PATTERN_RINHO, sentence);
        if (rinhoParser.matches()) {
            return decodeRinho(channel, remoteAddress, sentence, rinhoParser);
        }

        Parser parser = new Parser(PATTERN, sentence);
        if (!parser.matches()) {
            return null;
        }

        boolean isAkroz = sentence.startsWith("RUV");

        Position position = new Position(getProtocolName());

        Boolean valid = null;
        Integer event = null;

        if (parser.hasNext(3)) {
            event = parser.nextInt();
            position.setTime(getTime(parser.nextInt(0), parser.nextInt(0), parser.nextInt(0)));
        } else if (parser.hasNext()) {
            position.setTime(getTime(parser.nextInt(0)));
        }

        if (parser.hasNext()) {
            event = parser.nextInt();
        }

        if (parser.hasNext(6)) {
            position.setTime(parser.nextDateTime(Parser.DateTimeFormat.DMY_HMS));
        }

        boolean located = false;
        if (parser.hasNext(4)) {
            position.setLatitude(parser.nextCoordinate(Parser.CoordinateFormat.DEG_DEG));
            position.setLongitude(parser.nextCoordinate(Parser.CoordinateFormat.DEG_DEG));
            located = true;
        }
        if (parser.hasNext(6)) {
            position.setLatitude(parser.nextCoordinate(Parser.CoordinateFormat.HEM_DEG_MIN));
            position.setLongitude(parser.nextCoordinate(Parser.CoordinateFormat.HEM_DEG_MIN));
            located = true;
        }

        position.setSpeed(convertSpeed(parser.nextDouble(0), isAkroz ? "kmh" : "mph"));
        position.setCourse(parser.nextDouble(0));

        if (parser.hasNext(2)) {
            int fixMode = parser.nextInt();
            valid = isAkroz ? fixMode >= 2 && fixMode < 8 : fixMode > 0;
            int input = parser.nextHexInt();
            position.set(Position.KEY_IGNITION, BitUtil.check(input, 7));
            if (isAkroz) {
                position.set(Position.KEY_CHARGE, BitUtil.check(input, 6));
            }
            position.set(Position.KEY_INPUT, input);
        }

        if (parser.hasNext(7)) {
            position.set(Position.KEY_ODOMETER, parser.nextInt());
            position.set(Position.KEY_POWER, parser.nextInt() / 100.0);
            position.set(Position.KEY_BATTERY, parser.nextInt() / 100.0);
            position.set(Position.KEY_RPM, parser.nextInt());
            position.set(Position.PREFIX_TEMP + 1, parser.nextDouble());
            position.set(Position.PREFIX_TEMP + 2, parser.nextDouble());
            event = parser.nextHexInt();
        }

        if (parser.hasNext(2)) {
            int value = parser.nextInt();
            if (!isAkroz) {
                event = value;
            }
            position.set(Position.KEY_HDOP, parser.nextInt());
        }

        if (parser.hasNext(3)) {
            position.set(Position.KEY_INPUT, parser.nextHexInt(0));
            position.set(Position.KEY_BATTERY, parser.nextInt(0));
            position.set(Position.KEY_ODOMETER, parser.nextLong(16, 0));
        }

        if (parser.hasNext(3)) {
            valid = parser.nextInt() > 0;
            position.set(Position.KEY_PDOP, parser.nextInt());
            position.set(Position.KEY_RSSI, parser.nextInt());
        }
        if (parser.hasNext(2)) {
            position.set(Position.PREFIX_TEMP + 1, parser.nextInt() / 100.0);
            position.set(Position.PREFIX_TEMP + 2, parser.nextInt() / 100.0);
        }

        position.setValid(valid == null || valid);

        if (isAkroz) {
            String[] values = sentence.substring(0, sentence.indexOf(';')).split(",", -1);
            int index = 3;
            switch (Integer.parseInt(sentence.substring(3, 5))) {
                case 0 -> {
                    String voltage = values[index++];
                    position.set(Position.KEY_BATTERY, Integer.parseInt(voltage.substring(0, 4)) * 0.01);
                    position.set(Position.KEY_POWER, Integer.parseInt(voltage.substring(4)) * 0.01);
                    position.set("serial", values[index++]);
                    position.set(Position.KEY_VERSION_FW, values[index++]);
                    position.set(Position.KEY_VERSION_HW, values[index++]);
                    index += 5;
                    if (!values[index].isEmpty()) {
                        position.set(Position.KEY_ICCID, values[index]);
                    }
                }
                case 1 -> {
                    String voltage = values[index++];
                    position.set(Position.KEY_BATTERY, Integer.parseInt(voltage.substring(0, 4)) * 0.01);
                    position.set(Position.KEY_POWER, Integer.parseInt(voltage.substring(4)) * 0.01);
                    index += 3;
                    position.set(Position.KEY_HOURS, Long.parseLong(values[index++]) * 60000);
                    position.set(Position.KEY_ODOMETER, Long.parseLong(values[index++]));
                    position.set(Position.KEY_RPM, Integer.parseInt(values[index++]));
                    position.set(Position.KEY_COOLANT_TEMP, Integer.parseInt(values[index++]));
                    position.set("oilPressure", Integer.parseInt(values[index++]));
                    position.set(Position.KEY_FUEL_LEVEL, Integer.parseInt(values[index++]));
                    index += 3;
                    position.set(Position.KEY_DRIVER_UNIQUE_ID, values[index]);
                }
                case 2 -> {
                    index += 8;
                    position.set("tripTime", Integer.parseInt(values[index++]));
                    position.set("tripDistance", Long.parseLong(values[index++]));
                    position.set(Position.KEY_FUEL_USED, Long.parseLong(values[index]) / 10.0);
                }
                default -> {
                    position.set(Position.KEY_THROTTLE, Integer.parseInt(values[index++]));
                    position.set(Position.KEY_HOURS, Long.parseLong(values[index++]) * 60000);
                    position.set(Position.KEY_ODOMETER, Long.parseLong(values[index++]));
                    position.set(Position.KEY_RPM, Integer.parseInt(values[index++]));
                    position.set(Position.KEY_COOLANT_TEMP, Integer.parseInt(values[index++]));
                    position.set("oilPressure", Integer.parseInt(values[index++]));
                    position.set(Position.KEY_FUEL_LEVEL, Integer.parseInt(values[index++]));
                    position.set(Position.KEY_FUEL_USED, Long.parseLong(values[index++]) / 10.0);
                    index += 1;
                    position.set(Position.KEY_OBD_SPEED, Integer.parseInt(values[index++]));
                    position.set("engineTorque", Integer.parseInt(values[index++]));
                    index += 1;
                    position.set("engineBrake", Integer.parseInt(values[index++]));
                    index += 3;
                    position.set("cruiseControl", BitUtil.check(Integer.parseInt(values[index++]), 0));
                    position.set("clutchState", BitUtil.check(Integer.parseInt(values[index++]), 6));
                    position.set("parkingBrake", BitUtil.check(Integer.parseInt(values[index++]), 2));
                    position.set("serviceBrake", BitUtil.check(Integer.parseInt(values[index]), 3));
                }
            }
        }

        if (event != null) {
            position.set(Position.KEY_EVENT, event);
            if (isAkroz) {
                position.addAlarm(decodeAlarmAkroz(event));
            } else if (sentence.charAt(5) == ',') {
                position.addAlarm(decodeAlarm2(event));
            } else {
                position.addAlarm(decodeAlarm(event));
            }
        }

        Position result = decodeAttributes(channel, remoteAddress, position, splitAttributes(sentence));
        if (result != null && !located) {
            getLastLocation(result, result.getDeviceTime());
        }
        return result;
    }

    private String[] splitAttributes(String sentence) {
        int beginAttributes = sentence.indexOf(';');
        if (beginAttributes == -1) {
            return null;
        }
        int endIndex = sentence.indexOf('<', beginAttributes);
        if (endIndex == -1) {
            endIndex = sentence.length();
        }
        return sentence.substring(beginAttributes, endIndex).split(";");
    }

    private Position decodeRinho(Channel channel, SocketAddress remoteAddress, String sentence, Parser parser) {

        Position position = new Position(getProtocolName());

        String type = parser.next();
        position.set(Position.KEY_EVENT, parser.nextHexInt());

        int day = parser.nextInt();
        int month = parser.nextInt();
        int year = parser.nextInt();
        DateBuilder dateBuilder = new DateBuilder()
                .setDate(year, month, day)
                .setTime(parser.nextInt(), parser.nextInt(), parser.nextInt());

        double latitude = parser.nextCoordinate(Parser.CoordinateFormat.DEG_DEG);
        double longitude = parser.nextCoordinate(Parser.CoordinateFormat.DEG_DEG);

        position.setSpeed(UnitsConverter.knotsFromKph(parser.nextInt()));
        position.setCourse(parser.nextInt());

        int input = parser.nextHexInt();
        position.set(Position.KEY_IGNITION, BitUtil.check(input, 7));
        position.set(Position.KEY_INPUT, input);
        position.set(Position.KEY_OUTPUT, parser.nextHexInt());
        position.set(Position.KEY_POWER, parser.nextInt() / 10.0);
        position.set(Position.KEY_ODOMETER, parser.nextLong(16, 0));

        boolean gpsPower = parser.nextInt() == 1;
        int fixMode = parser.nextInt();
        position.set(Position.KEY_PDOP, parser.nextInt());
        position.set(Position.KEY_SATELLITES, parser.nextInt());
        position.set("dataAge", parser.nextHexInt());
        position.set(Position.KEY_RSSI, parser.nextInt());

        // reports without fix carry zero date and coordinates
        boolean located = day != 0;
        if (located) {
            position.setTime(dateBuilder.getDate());
            position.setLatitude(latitude);
            position.setLongitude(longitude);
            position.setValid(gpsPower && fixMode >= 2);
        } else {
            position.setTime(new Date());
        }

        List<String> attributes = new ArrayList<>();
        String[] segments = splitAttributes(sentence);
        if (segments != null) {
            for (String segment : segments) {
                if (!type.equals("CQ") && segment.matches("[0-9A-F]+[=!].*")) {
                    decodeCan(position, type, segment);
                } else {
                    attributes.add(segment);
                }
            }
        }

        Position result = decodeAttributes(channel, remoteAddress, position, attributes.toArray(new String[0]));
        if (result != null && !located) {
            getLastLocation(result, null);
        }
        return result;
    }

    private void decodeCan(Position position, String type, String segment) {
        for (String pair : segment.split(",")) {
            int index = pair.indexOf('=');
            if (index <= 0 || index == pair.length() - 1) {
                continue; // "CODE!error" or empty value means the ECU did not provide it
            }
            String code = pair.substring(0, index);
            String value = pair.substring(index + 1);
            if (type.equals("ER")) {
                switch (code) {
                    case "2010" -> position.set(Position.KEY_RPM, (int) Double.parseDouble(value));
                    case "5000" -> position.set(Position.KEY_THROTTLE, Double.parseDouble(value));
                    case "1030" -> position.set(Position.KEY_FUEL_USED, Double.parseDouble(value));
                    case "4201" -> position.set(Position.KEY_FUEL_LEVEL, Double.parseDouble(value));
                    case "1020" -> position.set(Position.KEY_OBD_ODOMETER, (long) (Double.parseDouble(value) * 1000));
                    case "1010" -> position.set(Position.KEY_OBD_SPEED, Double.parseDouble(value));
                    case "2012" -> position.set(Position.KEY_COOLANT_TEMP, Double.parseDouble(value));
                    case "2013" -> position.set("oilPressure", Double.parseDouble(value));
                    case "2020" -> position.set(Position.KEY_HOURS, (long) (Double.parseDouble(value) * 3600000));
                    case "3010" -> position.set("tripFuel", Double.parseDouble(value));
                    default -> position.set("can" + code, value);
                }
            } else {
                switch (code) {
                    case "1" -> position.set(Position.KEY_VIN, value);
                    case "2" -> position.set(Position.KEY_RPM, Integer.parseInt(value));
                    case "3" -> position.set(Position.KEY_OBD_SPEED, Integer.parseInt(value));
                    case "B" -> position.set(Position.KEY_OBD_ODOMETER, Long.parseLong(value) * 1000);
                    case "14" -> position.set(Position.KEY_FUEL_USED, Integer.parseInt(value));
                    case "15" -> position.set(Position.KEY_FUEL_LEVEL, Integer.parseInt(value));
                    case "2A" -> position.set(Position.KEY_COOLANT_TEMP, Integer.parseInt(value));
                    case "2C" -> position.set("oilPressure", Integer.parseInt(value));
                    default -> position.set("can" + code, value);
                }
            }
        }
    }

    private Position decodeAttributes(
            Channel channel, SocketAddress remoteAddress, Position position, String[] attributes) {

        String uniqueId = null;
        DeviceSession deviceSession = null;
        String messageIndex = null;
        boolean indexFirst = true;

        if (attributes != null) {
            for (String attribute : attributes) {
                int index = attribute.indexOf('=');
                if (index != -1) {
                    String key = attribute.substring(0, index).toLowerCase(Locale.ROOT);
                    String value = attribute.substring(index + 1);
                    switch (key) {
                        case "id" -> {
                            uniqueId = value;
                            deviceSession = getDeviceSession(channel, remoteAddress, value);
                            if (deviceSession != null) {
                                position.setDeviceId(deviceSession.getDeviceId());
                            }
                            if (messageIndex == null) {
                                indexFirst = false;
                            }
                        }
                        case "io" -> {
                            position.set(Position.KEY_IGNITION, BitUtil.check(value.charAt(0) - '0', 0));
                            position.set(Position.KEY_CHARGE, BitUtil.check(value.charAt(0) - '0', 1));
                            position.set(Position.KEY_OUTPUT, value.charAt(1) - '0');
                            position.set(Position.KEY_INPUT, value.charAt(2) - '0');
                        }
                        case "ix" -> position.set(Position.PREFIX_IO + 1, value);
                        case "ad" -> position.set(Position.PREFIX_ADC + 1, Integer.parseInt(value));
                        case "sv" -> position.set(Position.KEY_SATELLITES, Integer.parseInt(value));
                        case "bl" -> position.set(Position.KEY_BATTERY, Integer.parseInt(value) / 1000.0);
                        case "vo" -> position.set(Position.KEY_ODOMETER, Long.parseLong(value));
                        default -> position.set(key, value);
                    }
                } else if (attribute.startsWith("#")) {
                    messageIndex = attribute;
                }
            }
        }

        if (deviceSession != null) {
            if (channel != null) {
                if (messageIndex != null) {
                    String response;
                    if (messageIndex.startsWith("#IP")) {
                        response = ">SAK;ID=" + uniqueId + ";" + messageIndex + "<";
                    } else {
                        if (indexFirst) {
                            response = ">ACK;" + messageIndex + ";ID=" + uniqueId + ";";
                        } else {
                            response = ">ACK;ID=" + uniqueId + ";" + messageIndex + ";";
                        }
                        String model = getDeviceModel(deviceSession);
                        boolean lantrix = model != null && model.toUpperCase(Locale.ROOT).startsWith("LANTRIX");
                        int checksum = Checksum.xor(lantrix ? response : response + "*");
                        response += String.format("*%02X", checksum) + "<";
                    }
                    channel.writeAndFlush(new NetworkMessage(response, remoteAddress));
                } else {
                    channel.writeAndFlush(new NetworkMessage(uniqueId, remoteAddress));
                }
            }
            return position;
        }

        return null;
    }

}
