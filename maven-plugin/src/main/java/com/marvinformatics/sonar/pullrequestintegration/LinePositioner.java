package com.marvinformatics.sonar.pullrequestintegration;

import java.util.Map;

import com.google.common.collect.Maps;

public class LinePositioner {

	private Map<Integer, Integer> lineToPosition = Maps.newLinkedHashMap();

	public LinePositioner(String patch) {
		String[] lines = patch.split( "[\\r\\n]+" );
		int position = 0;
		int currentLine = -1;
		for (String line : lines) {
			if (line.startsWith( "@@" ))
				currentLine = new Integer( line.replaceAll( "@@.*\\+", "" ).replaceAll( "\\,.*", "" )
						.replaceAll( "\\D", "" ) );

			if (line.startsWith( "+" ))
				lineToPosition.put( currentLine, position );

			if (!line.startsWith( "-" )&&!line.startsWith( "@@" ))
				currentLine++;
			position++;
		}
	}

	public int toPostion(int line) {
		if (lineToPosition.containsKey( line ))
			return lineToPosition.get( line );

		return -1;
	}

	public int size() {
		return lineToPosition.size();
	}

}
