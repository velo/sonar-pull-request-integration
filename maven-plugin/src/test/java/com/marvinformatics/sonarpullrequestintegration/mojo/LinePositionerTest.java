package com.marvinformatics.sonarpullrequestintegration.mojo;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.marvinformatics.sonarpullrequestintegration.mojo.LinePositioner;

public class LinePositionerTest {

	@Test
	public void newFile() throws IOException {
		LinePositioner lp = new LinePositioner(Files.toString(new File(
				"src/test/resources/newFile.patch"), Charsets.UTF_8));

		Assert.assertEquals(170, lp.size());
		Assert.assertEquals(5, lp.toPostion(5));
		Assert.assertEquals(146, lp.toPostion(146));
		Assert.assertEquals(170, lp.toPostion(170));
		Assert.assertEquals(-1, lp.toPostion(171));
	}
	
	@Test
	public void inclusionsExclusion() throws IOException {
		LinePositioner lp = new LinePositioner(Files.toString(new File(
				"src/test/resources/inclusionsExclusion.patch"), Charsets.UTF_8));
		
		Assert.assertEquals(18, lp.size());
		Assert.assertEquals(-1, lp.toPostion(5));
		Assert.assertEquals(8, lp.toPostion(328));
		Assert.assertEquals(13, lp.toPostion(333));
		Assert.assertEquals(-1, lp.toPostion(338));
		Assert.assertEquals(18, lp.toPostion(344));
		Assert.assertEquals(20, lp.toPostion(345));
		Assert.assertEquals(-1, lp.toPostion(346));
	}

}
