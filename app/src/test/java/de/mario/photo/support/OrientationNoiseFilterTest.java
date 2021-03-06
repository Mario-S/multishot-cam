package de.mario.photo.support;

import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;

import static junit.framework.Assert.assertEquals;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class OrientationNoiseFilterTest {

    private static final BigDecimal ERROR = new BigDecimal(1);

    private OrientationNoiseFilter classUnderTest;

    @Before
    public void setUp() throws InterruptedException {
        classUnderTest = new OrientationNoiseFilter();
    }


    @Test
    public void testFilter_horizontal() {
        classUnderTest.filter(270);
        int result = classUnderTest.filter(275);
        assertEquals(0, result);
    }

    @Test
    public void testFilter_vertical_toLeft() {
        classUnderTest.filter(4);
        classUnderTest.filter(2);
        int result = classUnderTest.filter(359);
        assertThat(new BigDecimal(result), is(closeTo(new BigDecimal(0), ERROR)));
    }

    @Test
    public void testFilter_vertical_toRight() {
        classUnderTest.filter(358);
        int result = classUnderTest.filter(6);
        BigDecimal expected = new BigDecimal(result % OrientationNoiseFilter.MAX);
        assertThat(new BigDecimal(result), is(closeTo(expected, ERROR)));
    }
}
