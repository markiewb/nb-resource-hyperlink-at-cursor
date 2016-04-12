[![Build Status](https://travis-ci.org/markiewb/nb-resource-hyperlink-at-cursor.svg?branch=master)](https://travis-ci.org/markiewb/nb-resource-hyperlink-at-cursor)
[![Donate](https://www.paypalobjects.com/en_US/i/btn/btn_donate_SM.gif)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=K4CMP92RZELE2)

nb-resource-hyperlink-at-cursor
===============================
This plugin adds hyperlinks to filenames within String literals of Java sources. If you click the hyperlink, then the file will be opened in the NetBeans editor.

Download at http://plugins.netbeans.org/plugin/52349/?show=true


This plugin adds hyperlinks to filenames within String literals of Java sources. If you click the hyperlink, then the file will be opened in the NetBeans editor.

<p>
Features:
<ul>
<li>Supports relative paths regarding to the current file</li>
<li>Supports relative paths regarding to the current project (source, test and resources roots)</li>
<li>Supports absolute paths</li>
<li>Support partial matches (relative to current dir and source roots) - can be disabled at Options|Misc</li>
<li>Supports paths relative to project directory (since 1.2.0)</li>
<li>Supports fully qualified classnames (since 1.3.0)</li>
<li>Supports files in the same package but in different source root (since 1.3.0)</li>
</ul>


</p>

<img src="https://raw.github.com/markiewb/nb-resource-hyperlink-at-cursor/master/doc/screenshot-1.0.0.png"/>

<h2>Updates in 1.3.2:</h2>
<ul>
<li>[<a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/3">Issue 3</a>]:  Fixed freeze</li>
</ul>

<h2>Updates in 1.3.1:</h2>
<ul>
<li>[<a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/18">Issue 18</a>]:  NPE when pressing CTRL in the diff dialog</li>
</ul>

<h2>Updates in 1.3.0:</h2>
<ul>
<li>[<a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/12">Feature 12</a>]:  Support fully qualified classnames</li>
<li>[<a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/14">Feature 14</a>]:  Search for classname in dependencies too (only works for dependencies with sources)</li>
<li>[<a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/10">Feature 10</a>]:  Find files in same package but different source root</li>
<li>[<a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/16">Issue 16</a>]:  Make the hyperlinking faster / use less IO</li>

</ul>

<h2>Updates in 1.2.2:</h2>
<ul>
<li>[<a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/11">Issue 11</a>]:  Fixed: NPE</li>
</ul>

<h2>Updates in 1.2.1:</h2>
<ul>
<li>[<a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/9">Issue 9</a>]:  Fixed: Links to src/test/resources do not work</li>
</ul>


<h2>Updates in 1.2.0:</h2>
<ul>
<li>[<a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/8">Feature 8</a>]:  Support of paths relative to project directory</li>

<li>[<a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/7">Issue 7</a>]: Fixed: NPE at ResourceHyperlinkProvider.findFiles</li>
</ul>

<h2>Updates in 1.1.2:</h2>
<ul>
<li>[<a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/6">Issue 6</a>]: Fixed: NPE at ResourceHyperlinkProvider.getMatchingFileInCurrentDirectory</li>
</ul>

<h2>Updates in 1.1.1:</h2>
<ul>
<li>[<a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/5">Issue 5</a>]: Fixed: Multiple matches: Selected file in dialog will open wrong file</li>
</ul>

<h2>Updates in 1.1.0:</h2>
<ul>
<li>[<a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/1">Feature 1</a>]: Support of partial matching (+ options dialog)</li>
<li>[<a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues/4">Issue 4</a>]: Fixed: Provide a valid category for the update center</li>
</ul>


<h2>Updates in 1.0.0:</h2>
<ul>
<li>initial version - implements the RFE  <a href="https://netbeans.org/bugzilla/show_bug.cgi?id=237902">https://netbeans.org/bugzilla/show_bug.cgi?id=237902</a></li>
</ul>

<p>Provide defects, request for enhancements and feedback at <a href="https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues">https://github.com/markiewb/nb-resource-hyperlink-at-cursor/issues</a></p>
<p>Compatible to >=NB 7.2.1</p>
<p>Legal disclaimer: Code is licensed under Apache 2.0.</p>
<p>
<a href="https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=K4CMP92RZELE2"><img src="https://www.paypalobjects.com/en_US/i/btn/btn_donate_SM.gif" border="0"></a>
</p>
