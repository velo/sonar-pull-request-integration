package com.marvinformatics.sonar.pullrequestintegration;

import java.util.Map;

import com.google.common.collect.Maps;

public class LinePositioner {

	private Map<Integer, Integer> lineToPosition = Maps.newLinkedHashMap();

	public LinePositioner(String patch) {
		String[] lines = patch.split("[\\r\\n]+");
		int position = 1;
		int currentLine = -1;
		for (String line : lines) {
			if (line.startsWith("@@")) {
				// @@ -0,0 +1,138 @@
				currentLine = new Integer(line.replaceAll("@@.*\\+", "").replaceAll("\\,.*", "").replaceAll("\\D", ""));
				continue;
			}

			if (line.startsWith("-")) {
				position++;
				continue;
			}

			lineToPosition.put(currentLine, position);

			currentLine++;
			position++;
		}
	}

	public int toPostion(int line) {
		if (lineToPosition.containsKey(line))
			return lineToPosition.get(line);

		return 0;
	}

	public int size() {
		return lineToPosition.size();
	}

}
