package com.dashlabs.dash.timezone;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * User: cpaul
 * Date: 11/4/13
 * Time: 2:52 PM
 */

public class TimeZoneHelper {

    private static final String POLYGONS_FILE = "timezones/polygons.txt";

    private static final String WORLDTIME_FILE = "timezones/worldtime-sample.xml";

    private final HashMap<String, List<Coordinate>> polygonMap = new HashMap<String, List<Coordinate>>();

    private final HashMap<String, Rule> worldTimeMap = new HashMap<String, Rule>();

    private static final Map<String, Integer> daysMap;
    static {
        daysMap = new HashMap<String, Integer>();
        daysMap.put("SUN", Calendar.SUNDAY);
        daysMap.put("MON", Calendar.MONDAY);
        daysMap.put("TUE", Calendar.TUESDAY);
        daysMap.put("WED", Calendar.WEDNESDAY);
        daysMap.put("THU", Calendar.THURSDAY);
        daysMap.put("FRI", Calendar.FRIDAY);
        daysMap.put("SAT", Calendar.SATURDAY);
    }

    private static final Map<String, Integer> monthsMap;
    static{
        monthsMap = new HashMap<String, Integer>();
        monthsMap.put("JAN", Calendar.JANUARY);
        monthsMap.put("FEB", Calendar.FEBRUARY);
        monthsMap.put("MAR", Calendar.MARCH);
        monthsMap.put("APR", Calendar.APRIL);
        monthsMap.put("MAY", Calendar.MARCH);
        monthsMap.put("JUN", Calendar.JUNE);
        monthsMap.put("JUL", Calendar.JULY);
        monthsMap.put("AUG", Calendar.AUGUST);
        monthsMap.put("SEP", Calendar.SEPTEMBER);
        monthsMap.put("OCT", Calendar.OCTOBER);
        monthsMap.put("NOV", Calendar.NOVEMBER);
        monthsMap.put("DEC", Calendar.DECEMBER);
    }

    public void initializePolygonMap() throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(POLYGONS_FILE);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String s = "";
        List<String> files = new ArrayList<String>();
        while ((s = reader.readLine()) != null) {
            files.add(s);
        }

        XmlMapper mapper = new XmlMapper();
        AnnotationIntrospector introspector = new JaxbAnnotationIntrospector(mapper.getTypeFactory());
        mapper.getDeserializationConfig().with(introspector);
        mapper.getSerializationConfig().with(introspector);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        for(String filename : files) {
            String ext = filename.length() > 4 ? filename.substring(filename.length() - 4) : filename;
            int count = 0;
            if (ext.equalsIgnoreCase(".kml")) {
                String locationId = filename.replace(".kml", "");
                InputStream file = getClass().getClassLoader().getResourceAsStream(String.format("timezones/%s",filename));
                Kml kml = mapper.readValue(file, Kml.class);
                Document document = kml.getDocument();
                if (document.getPlacemark() == null) {
                    Folder folder = document.getFolder();

                    List<Placemark> placemarks = folder.getPlacemarks();
                    if (placemarks == null) {
                        //1 Document, 1 Folder, MANY Provinces...and FOR EACH Province, 1 Polygon
                        List<Province> provinces = folder.getProvinces();
                        for(Province province : provinces) {
                            List<Coordinate> coordinates = province.getPolygon().getOuterBoundary().getLinearRing().getCoordinates();
                            polygonMap.put(String.format("%s+%s", locationId, province.getName()), coordinates);
                        }
                        continue;
                    }

                    //1 Document, 1 Folder, MANY Placemarks...
                    for(Placemark placemark : placemarks) {
                        if (placemark.getPolygon() == null) {
                            //... and FOR EACH Placemark, 1 MultiGeometry, MANY POLYGONS
                            MultiGeometry multiGeometry = placemark.getMultiGeometry();
                            List<Polygon> polygons = multiGeometry.getPolygons();
                            for(Polygon polygon :  polygons) {
                                List<Coordinate> coordinates = polygon.getOuterBoundary().getLinearRing().getCoordinates();
                                polygonMap.put(String.format("%s+%s-%d", locationId, placemark.getName(), count++), coordinates);
                            }
                            continue;
                        }

                        //... and FOR EACH Placemark, 1 Polygon
                        List<Coordinate> coordinates = placemark.getPolygon().getOuterBoundary().getLinearRing().getCoordinates();
                        polygonMap.put(String.format("%s+%s", locationId, placemark.getName()), coordinates);
                    }
                    continue;
                }

                if (document.getPlacemark().getPolygon() == null) {
                    //1 Document, 1 Placemark, 1 MultiGeometry, MANY Pologons
                    MultiGeometry multiGeometry = document.getPlacemark().getMultiGeometry();
                    List<Polygon> polygons = multiGeometry.getPolygons();
                    for(Polygon polygon :  polygons) {
                        List<Coordinate> coordinates = polygon.getOuterBoundary().getLinearRing().getCoordinates();
                        polygonMap.put(String.format("%s+%s-%d", locationId, document.getPlacemark().getName(), count++), coordinates);
                    }
                    continue;
                }

                //1 Document, 1 Placemark, 1 Polygon
                List<Coordinate> coordinates = document.getPlacemark().getPolygon().getOuterBoundary().getLinearRing().getCoordinates();
                polygonMap.put(locationId, coordinates);
            }
        }
    }

    public void initializeWorldTimeMap() throws IOException, ParseException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(WORLDTIME_FILE);

        XmlMapper mapper = new XmlMapper();
        AnnotationIntrospector introspector = new JaxbAnnotationIntrospector(mapper.getTypeFactory());
        mapper.getDeserializationConfig().with(introspector);
        mapper.getSerializationConfig().with(introspector);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        TimeZoneData timeZoneData = mapper.readValue(inputStream, TimeZoneData.class);
        for (Location location : timeZoneData.getLocations()) {
            worldTimeMap.put(location.getLocationId(), getEffectiveRule(location.getTimeRules().getRules()));
        }
    }

    public TimeZone getTimeZoneForCoordinates(double lat, double lng) {
        for (Map.Entry<String, List<Coordinate>> entry : polygonMap.entrySet()) {
            List<Coordinate> coordinates = entry.getValue();
            if (checkPointInPolygon(lat, lng, coordinates)) {
                String locationId = entry.getKey();
                int plusIndex = locationId.indexOf("+");
                if (plusIndex != -1) {
                    locationId = entry.getKey().substring(0, plusIndex);
                }

                if(worldTimeMap.get(locationId).getDstBias() == null || worldTimeMap.get(locationId).getDstBias().isEmpty())
                    return TimeZone.getTimeZone(String.format("GMT%s", worldTimeMap.get(locationId).getStdBias()));
                else {
                    Calendar start = getDstCalendar(locationId, worldTimeMap.get(locationId).getDstStart());
                    Calendar end = getDstCalendar(locationId, worldTimeMap.get(locationId).getDstEnd());

                    if(end.before(start)) { //Does DST end next year? (see: Australia timezones for example)
                        end.set(Calendar.YEAR, end.get(Calendar.YEAR)+1);
                    }

                    String dstOrdinalPositionStart = worldTimeMap.get(locationId).getDstStart().substring(5, 8);
                    String dstOrdinalPositionEnd = worldTimeMap.get(locationId).getDstEnd().substring(5, 8);
                    Calendar today = Calendar.getInstance(TimeZone.getTimeZone(String.format("GMT%s", worldTimeMap.get(locationId).getStdBias())));
                    if(getCalendarWithDayOfWeekInMonth(start, dstOrdinalPositionStart).before(today)
                            && getCalendarWithDayOfWeekInMonth(end, dstOrdinalPositionEnd).after(today)) {
                        return TimeZone.getTimeZone(String.format("GMT%s", worldTimeMap.get(locationId).getDstBias()));
                    } else {
                        return TimeZone.getTimeZone(String.format("GMT%s", worldTimeMap.get(locationId).getStdBias()));
                    }
                }
            }
        }

        return null;
    }

    // {@literal http://www.ecse.rpi.edu/Homepages/wrf/Research/Short_Notes/pnpoly.html}
    private boolean checkPointInPolygon(double lat, double lng, List<Coordinate> coordinates) {
        int i, j;
        boolean result = false;
        for (i = 0, j = coordinates.size()-1; i < coordinates.size(); j = i++) {
            Coordinate iCoordinate = coordinates.get(i);
            Coordinate jCoordinate = coordinates.get(j);
            if (((iCoordinate.getLongitude()>lng) != (jCoordinate.getLongitude()>lng)) &&
                    (lat < (jCoordinate.getLatitude()-iCoordinate.getLatitude()) *
                            (lng-iCoordinate.getLongitude()) / (jCoordinate.getLongitude()-iCoordinate.getLongitude()) + iCoordinate.getLatitude())) {
                result = !result;
            }
        }
        return result;
    }

    private Rule getEffectiveRule(List<Rule> rules) throws ParseException {
        if (rules.size() > 1) {
            List<Date> effectiveDates = new ArrayList<Date>();
            HashMap<Date, Rule> map = new HashMap<Date, Rule>();
            for (Rule rule : rules) {
                String effectiveString = rule.getEffective().replaceAll("Z", "");
                Date effectiveDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ").parse(String.format("%s%s",effectiveString,rule.getStdBias()));
                effectiveDates.add(effectiveDate);
                map.put(effectiveDate, rule);
            }
            Collections.sort(effectiveDates);
            Collections.reverse(effectiveDates);

            for(Date date : effectiveDates) {
                if (date.before(new Date()))  {
                    return map.get(date);
                }
            }
            return null;
        } else if (rules.size() == 1) {
            return rules.get(0);
        } else {
            return null;
        }
    }

    private Calendar getDstCalendar(String locationId, String dateString) {
        String dstHour = dateString.substring(0, 2);
        String dstMin = dateString.substring(2, 4);
        String dstDay = dateString.substring(9, 12);
        String dstMonth = dateString.substring(13, 16);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(String.format("GMT%s", worldTimeMap.get(locationId).getStdBias())));
        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(dstHour));
        cal.set(Calendar.MINUTE, Integer.parseInt(dstMin));
        cal.set(Calendar.DAY_OF_WEEK, daysMap.get(dstDay));
        cal.set(Calendar.MONTH, monthsMap.get(dstMonth));

        return cal;
    }

    private Calendar getCalendarWithDayOfWeekInMonth(Calendar cal, String ordinalPosition) {
        if (ordinalPosition.equals("1ST")) {
            cal.set(Calendar.DAY_OF_WEEK_IN_MONTH, 1);
        } else if (ordinalPosition.equals("2ND")) {
            cal.set(Calendar.DAY_OF_WEEK_IN_MONTH, 2);
        } else if (ordinalPosition.equals("3RD")) {
            cal.set(Calendar.DAY_OF_WEEK_IN_MONTH, 3);
        } else if (ordinalPosition.equals("4TH")) {
            cal.set(Calendar.DAY_OF_WEEK_IN_MONTH, 4);
        } else if (ordinalPosition.equals("LST")) {
            cal.set(Calendar.DAY_OF_WEEK_IN_MONTH, -1);
        }

        //be careful!! time is printed for us in the console in EST/EDT!!!!!!
        //System.out.printf("%n%s%n", cal.getTime());
        return cal;
    }
}
