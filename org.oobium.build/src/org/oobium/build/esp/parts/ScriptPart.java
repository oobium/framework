package org.oobium.build.esp.parts;

import static org.oobium.utils.CharStreamUtils.closer;

import org.oobium.build.esp.EspPart;

public class ScriptPart extends EspPart {

	public ScriptPart(EspPart parent, int start, int end) {
		super(parent, Type.ScriptPart, start, end);
		parse();
	}

	protected int commentCheck(EspPart parent, char[] ca, int ix) {
		if(ix >= 0) {
			if(ix < ca.length-1) {
				if(ca[ix] == '"' || ca[ix] == '\'') {
					ix = closer(ca, ix, end, true, true);
					if(ix == -1) {
						ix = end;
					}
				} else if(ca[ix] == '/' && (ca[ix+1] == '*' || ca[ix+1] == '/')) {
					CommentPart comment = new CommentPart(parent, ix);
					ix = comment.getEnd();
				}
			}
		}
		return ix;
	}

	private int findJavaEnd(int start, int end) {
		for(int i = start; i < end; i++) {
			switch(ca[i]) {
			case ',':
			case '}':
			case ';':
				return i;
			default:
				i = skip(ca, i, end);
			}
		}
		return end;
	}

	public boolean isSimple() {
		return true;
	}
	
	private int skip(char[] ca, int start, int end) {
		int i = start;
		switch(ca[i]) {
		case '"':
		case '\'':
			i = closer(ca, i, end, true, true);
			if(i == -1) {
				i = end;
			}
			break;
		case '/':
			if(ca[i-1] == '/') {
				for(i = i + 1; i < end && ca[i] != '\n'; i++);
			} else if(i < end-1 && ca[i+1] == '*') {
				for(i = i + 1; i < end && !(ca[i] == '/' && ca[i-1] == '*'); i++);
			}
			break;
		}
		return i;
	}
	
	private void parse() {
		for(int s = start; s < end; s++) {
			switch(ca[s]) {
			case '=':
				if(ca[s-1] == ':') {
					int s2 = findJavaEnd(s, end);
					new ScriptJavaPart(this, s-1, s2);
					s = s2 + 1;
				}
				break;
			case '"':
			case '\'':
				s = closer(ca, s, end, true, true);
				if(s == -1) {
					s = end;
				}
				break;
			case '*':
				if(ca[s-1] == '/') {
					CommentPart comment = new CommentPart(this, s-1);
					s = comment.getEnd();
				}
				break;
			case '/':
				if(ca[s-1] == '/') {
					CommentPart comment = new CommentPart(this, s-1);
					s = comment.getEnd();
				}
				break;
			}
		}
	}

}
