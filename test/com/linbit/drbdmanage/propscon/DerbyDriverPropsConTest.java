package com.linbit.drbdmanage.propscon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.Test;

import com.linbit.drbdmanage.security.AccessDeniedException;

public class DerbyDriverPropsConTest extends DerbyDriverPropsConBase
{
    @Test
    public void testPersistSimple() throws InvalidKeyException, InvalidValueException, SQLException
    {
        PropsContainer container = PropsContainer.createRootContainer(dbDriver);
        String expectedKey = "key";
        String expectedValue = "value";
        container.setProp(expectedKey, expectedValue);

        ResultSet resultSet = getAllContent();

        assertTrue("No entries found in the database", resultSet.next());
        String instanceName = resultSet.getString(1);
        String key = resultSet.getString(2);
        String value = resultSet.getString(3);

        assertEquals(DEFAULT_INSTANCE_NAME, instanceName);
        assertEquals(expectedKey, key);
        assertEquals(expectedValue, value);

        assertFalse("Unknown entries found in the database", resultSet.next());
    }

    @Test
    public void testPersistNested() throws InvalidKeyException, InvalidValueException, SQLException
    {
        PropsContainer container = PropsContainer.createRootContainer(dbDriver);
        Map<String, String> map = new HashMap<>();
        map.put("a", "c");
        map.put("a/b", "d");

        container.setAllProps(map, null);

        checkIfPresent(map, DEFAULT_INSTANCE_NAME);
    }

    @Test
    public void testPersistUpdate() throws InvalidKeyException, InvalidValueException, SQLException
    {
        PropsContainer container = PropsContainer.createRootContainer(dbDriver);
        Map<String, String> map = new HashMap<>();
        map.put("a", "c");

        container.setAllProps(map, null);

        // we don't have to test the results, as testPersistSimple handles that case

        map.put("a/b", "d");
        container.setAllProps(map, null);

        checkIfPresent(map, DEFAULT_INSTANCE_NAME);

        map.remove("a/b");
        container.removeProp("a/b");

        checkIfPresent(map, DEFAULT_INSTANCE_NAME);
    }

    @Test
    public void testPersistMultipleContainer() throws InvalidKeyException, InvalidValueException, SQLException
    {
        String expectedInstanceName1 = "INSTANCE_1";
        String expectedInstanceName2 = "INSTANCE_2";

        PropsConDerbyDriver driver1 = new PropsConDerbyDriver(expectedInstanceName1, dbConnPool);
        PropsConDerbyDriver driver2 = new PropsConDerbyDriver(expectedInstanceName2, dbConnPool);

        PropsContainer container1 = PropsContainer.createRootContainer(driver1);
        PropsContainer container2 = PropsContainer.createRootContainer(driver2);

        Map<String, String> map1 = new HashMap<>();
        map1.put("a", "b");
        map1.put("a/c", "d");
        map1.put("e", "f");
        Map<String, String> map2 = new HashMap<>();
        map2.put("a", "b");
        map2.put("g", "h");
        map2.put("g/c", "i");
        map2.put("g/j", "k");

        container1.setAllProps(map1, null);
        container2.setAllProps(map2, null);

        checkIfPresent(map1, expectedInstanceName1);
        checkIfPresent(map2, expectedInstanceName2);

        container1.clear();
        map1.clear();

        checkIfPresent(map1, expectedInstanceName1);
        checkIfPresent(map2, expectedInstanceName2);
    }

    @Test
    public void testLoadSimple() throws SQLException, InvalidKeyException, InvalidValueException
    {
        String instanceName = DEFAULT_INSTANCE_NAME;
        String key = "a";
        String value = "b";
        insert(instanceName, key, value);

        Props props = PropsContainer.loadContainer(dbDriver);

        Set<Entry<String,String>> entrySet = props.entrySet();
        assertEquals("Unexpected entries in PropsContainer", 1 , entrySet.size());
        assertTrue("PropsContainer missing key [" + key + "]", props.keySet().contains(key));
        assertEquals("Unexpected value", value, props.getProp(key));
    }

    @Test
    public void testLoadNested() throws SQLException, InvalidKeyException, InvalidValueException
    {
        String instanceName = DEFAULT_INSTANCE_NAME;
        String key1 = "a";
        String value1 = "b";
        String key2 = key1 + "/c";
        String value2 = "d";
        insert(instanceName, key1, value1);
        insert(instanceName, key2, value2);

        Props props = PropsContainer.loadContainer(dbDriver);
        assertEquals("Unexpected entries in PropsContainer", 2 , props.size());

        assertTrue("PropsContainer missing key [" + key1 + "]", props.keySet().contains(key1));
        assertTrue("PropsContainer missing key [" + key2 + "]", props.keySet().contains(key2));
        assertEquals("Unexpected value", value1, props.getProp(key1));
        assertEquals("Unexpected value", value2, props.getProp(key2));
    }

    @Test
    public void testLoadUpdate() throws SQLException, InvalidKeyException, AccessDeniedException, InvalidValueException
    {
        String instanceName = DEFAULT_INSTANCE_NAME;
        String key1 = "a";
        String key2 = key1 + "/" + "c";
        String value1 = "b";
        String value2 = "d";

        Map<String, String> map = new HashMap<>();

        insert(instanceName, key1, value1);
        map.put(key1, value1);

        Props props = PropsContainer.loadContainer(dbDriver);
        checkExpectedMap(map, props);

        insert(instanceName, key2, value2);
        map.put(key2, value2);

        props = PropsContainer.loadContainer(dbDriver);
        checkExpectedMap(map, props);

        delete(instanceName, key2);
        map.remove(key2);

        props = PropsContainer.loadContainer(dbDriver);
        checkExpectedMap(map, props);
    }

    @Test
    public void testLoadMultiple() throws SQLException, InvalidKeyException, AccessDeniedException, InvalidValueException
    {
        String instanceName1 = "INSTANCE_1";
        String instanceName2 = "INSTANCE_2";

        PropsConDerbyDriver driver1 = new PropsConDerbyDriver(instanceName1, dbConnPool);
        PropsConDerbyDriver driver2 = new PropsConDerbyDriver(instanceName2, dbConnPool);

        Map<String, String> map1 = new HashMap<>();
        map1.put("a", "b");
        map1.put("a/c", "d");
        map1.put("e", "f");
        Map<String, String> map2 = new HashMap<>();
        map2.put("a", "b");
        map2.put("g", "h");
        map2.put("g/c", "i");
        map2.put("g/j", "k");

        insert(instanceName1, map1);
        insert(instanceName2, map2);

        Props props1 = PropsContainer.loadContainer(driver1);
        Props props2 = PropsContainer.loadContainer(driver2);

        checkExpectedMap(map1, props1);
        checkExpectedMap(map2, props2);
    }
}
