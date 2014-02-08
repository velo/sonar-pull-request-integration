package com.marvinformatics.sonar.pullrequestintegration;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.marvinformatics.sonar.pullrequestintegration.LinePositioner;

public class LinePositionerTest {

	@Test
	public void newFile() throws IOException {
		LinePositioner lp = new LinePositioner(Files.toString(new File(
				"src/test/resources/newFile.patch"), Charsets.UTF_8));

		Assert.assertEquals(169, lp.size());
		Assert.assertEquals(5, lp.toPostion(5));
		Assert.assertEquals(146, lp.toPostion(146));
		Assert.assertEquals(-1, lp.toPostion(170));
		Assert.assertEquals(-1, lp.toPostion(171));
	}
	
	@Test
	public void inclusionsExclusion() throws IOException {
		LinePositioner lp = new LinePositioner(Files.toString(new File(
				"src/test/resources/inclusionsExclusion.patch"), Charsets.UTF_8));
		
		Assert.assertEquals(6, lp.size());
		Assert.assertEquals(-1, lp.toPostion(5));
		Assert.assertEquals(-1, lp.toPostion(328));
		Assert.assertEquals(10, lp.toPostion(329));
		Assert.assertEquals(11, lp.toPostion(330));
		Assert.assertEquals(12, lp.toPostion(331));
		Assert.assertEquals(13, lp.toPostion(332));
		Assert.assertEquals(-1, lp.toPostion(333));
		Assert.assertEquals(-1, lp.toPostion(338));
		Assert.assertEquals(-1, lp.toPostion(344));
		Assert.assertEquals(22, lp.toPostion(345));
		Assert.assertEquals(-1, lp.toPostion(346));
	}

}
