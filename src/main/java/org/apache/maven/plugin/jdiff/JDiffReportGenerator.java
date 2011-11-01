package org.apache.maven.plugin.jdiff;

import java.util.ResourceBundle;

import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.reporting.MavenReportException;

public class JDiffReportGenerator
{
    
    public void doGenerateReport( ResourceBundle bundle, Sink sink )
    throws MavenReportException
    {
        sink.head();
        sink.title();
        sink.text( "JDiff API Difference Report" );
        sink.title_();
        sink.head_();

        sink.body();
        sink.section1();

        sink.sectionTitle1();
        sink.text( "JDiff API Difference Report" );
        sink.sectionTitle1_();

        sink.paragraph();
        sink.text( "The pages generated by JDiff is on a separate page.  It can be found " );
        sink.link( "jdiff/changes.html" );
        sink.text( "here" );
        sink.link_();
        sink.text( "." );
        sink.paragraph_();

        sink.section1_();
        sink.body_();
    }

}