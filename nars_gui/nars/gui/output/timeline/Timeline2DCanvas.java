package nars.gui.output.timeline;

import com.google.common.collect.Lists;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import nars.core.Events.InferenceEvent;
import nars.entity.Item;
import nars.gui.NARSwing;
import nars.gui.output.chart.TimeSeries;
import nars.io.Output.IN;
import nars.io.Output.OUT;
import nars.io.Texts;
import nars.util.NARTrace;
import nars.util.NARTrace.OutputEvent;
import nars.util.NARTrace.TaskEvent;
import processing.core.PApplet;
import static processing.core.PConstants.SQUARE;
import processing.event.KeyEvent;

/**
 * Timeline view of an inference trace. Focuses on a specific window and certain
 * features which can be adjusted dynamically. Can either analyze a trace while
 * a NAR runs, or after it finishes.
 */
public class Timeline2DCanvas extends PApplet {

    float camScale = 1f;
    float scaleSpeed = 4f;
    private float lastMousePressY = Float.NaN;
    private float lastMousePressX = Float.NaN;

    final int defaultFrameRate = 25;
    
    boolean updating = true;

    float minLabelScale = 10f;
    float minYScale = 0.5f;
    float minTimeScale = 0.5f;
    float maxYScale = 1000f;
    float maxTimeScale = 1000f;
    float drawnTextScale = 0;

    //display options to extract to a parameter class ----------------------------------------
    boolean showItemLabels = true;
    float textScale = 0.1f;
    float timeScale = 32f;
    float yScale = 32f;
    long cycleStart = 0;
    long cycleEnd = 45;

    float camX = 0f;
    float camY = 0f;
    
    long lastUpdate = System.nanoTime();

    public final List<Chart> charts;
    //display options ----------------------------------------

    /**
     * Modes: Line Line with vertical pole to base Stacked bar Stacked bar
     * normalized each step Scatter Spectral Event Bubble
     *
     */
    abstract public static class Chart {

        float height = 1.0f;

        public Chart() {
            height = 1f;
        }

        public Chart height(float h) {
            this.height = h;
            return this;
        }
        
        //called during NAR thread
        public void update(Timeline2DCanvas l, float timeScale, float yScale) {
            
        }

        //called during Swing thread
        abstract public void draw(Timeline2DCanvas l, float y, float timeScale, float yScale);

    }

    public static class LineChart extends Chart {

        public final List<TimeSeries> sensors;

        double min;
        double max;
        boolean showVerticalLines = false;
        boolean showPoints = true;
        float lineThickness = 2f;

        public LineChart(TimeSeries t) {
            super();
            this.sensors = Lists.newArrayList(t);

        }

        public LineChart(TimeSeries... series) {
            super();
            this.sensors = Arrays.asList(series);            
        }
        
        public LineChart(NARTrace t, String... sensors) {
            super();
            this.sensors = Stream.of(sensors).map(x -> t.charts.get(x)).collect(toList());
        }

        @Override
        public void draw(Timeline2DCanvas l, float y, float timeScale, float yScale) {
            yScale *= height;

            float screenyHi = l.screenY(l.cycleStart * timeScale, y);
            float screenyLo = l.screenY(l.cycleStart * timeScale, y + yScale);

            updateRange(l);

            l.stroke(127);
            l.strokeWeight(0.5f);

            //bottom line
            l.line(l.cycleStart * timeScale, y+yScale, l.cycleEnd * timeScale, y+yScale);

            //top line
            l.line(l.cycleStart * timeScale, y, l.cycleEnd * timeScale, y);

            drawData(l, timeScale, yScale, y);

            drawOverlay(l, screenyLo, screenyHi);

        }

        protected void updateRange(Timeline2DCanvas l) {
            min = Double.NaN;
            max = 0;
            for (TimeSeries chart : sensors) {

                chart.updateMinMax(l.cycleStart, l.cycleEnd);

                if (Double.isNaN(min)) {
                    min = (chart.getMin());
                    max = (chart.getMax());
                } else {
                    min = Math.min(min, chart.getMin());
                    max = Math.max(max, chart.getMax());
                }
            }
        }

        protected void drawOverlay(Timeline2DCanvas l, float screenyLo, float screenyHi) {
            //draw overlay
            l.pushMatrix();
            l.resetMatrix();
            l.textSize(15f);

            int dsy = (int) Math.abs(screenyLo - screenyHi);

            float dsyt = screenyHi + 0.15f * dsy;
            float ytspace = dsy * 0.75f / sensors.size() / 2;
            for (TimeSeries chart : sensors) {
                l.fill(chart.getColor().getRGB());
                dsyt += ytspace;
                l.text(chart.label, 0, dsyt);
                dsyt += ytspace;
            }

            l.textSize(11f);
            l.fill(200, 195f);
            l.text(Texts.n4((float) min), 0, screenyLo - dsy / 10f);
            l.text(Texts.n4((float) max), 0, screenyHi + dsy / 10f);

            l.popMatrix();
        }

        protected void drawData(Timeline2DCanvas l, float timeScale1, float yScale1, float y) {
            int ccolor = 0;
            for (TimeSeries chart : sensors) {
                ccolor = chart.getColor().getRGB();
                float lx = 0, ly = 0;

                l.fill(255f);
                for (long t = l.cycleStart; t < l.cycleEnd; t++) {
                    float x = t * timeScale1;

                    float v = chart.getValue(t);
                    if (Float.isNaN(v)) {
                        continue;
                    }

                    float p = (max == min) ? 0 : (float) ((v - min) / (max - min));
                    float px = x;
                    float h = p * yScale1;
                    float py = y + yScale1 - h;

                    l.strokeWeight(lineThickness);

                    if (showVerticalLines) {
                        l.stroke(ccolor, 127f);
                        l.line(px, py, px, py + h);
                    }

                    l.stroke(ccolor);

                    if (t != l.cycleStart) {
                        l.line(lx, ly, px, py);
                    }
                    lx = px;
                    ly = py;

                    if (showPoints) {
                        l.noStroke();
                        l.fill(ccolor);
                        float w = Math.min(timeScale1, yScale1) / 12f;
                        l.rect(px - w / 2f, py - w / 2f, w, w);
                    }

                }
            }
        }

    }

    public static class StackedPercentageChart extends LineChart {

        float barWidth = 0.9f;
        float barHeight = 0.95f;

        public StackedPercentageChart(TimeSeries... series) {
            super(series);
        }
        
        public StackedPercentageChart(NARTrace t, String... sensors) {
            super(t, sensors);
        }

        @Override
        protected void updateRange(Timeline2DCanvas l) {
            super.updateRange(l);
            min = 0;
            max = 1.0f;
        }

        @Override
        protected void drawData(Timeline2DCanvas l, float timeScale, float yScale, float y) {

            l.noStroke();

            for (long t = l.cycleStart; t < l.cycleEnd; t++) {

                float total = 0;

                for (TimeSeries chart : sensors) {
                    float v = chart.getValue(t);
                    if (Float.isNaN(v)) {
                        continue;
                    }
                    total += v;
                }

                if (total == 0) {
                    continue;
                }

                float sy = y;

                for (TimeSeries chart : sensors) {
                    int ccolor = chart.getColor().getRGB();
                    float lx = 0, ly = 0;
                    l.strokeWeight(1f);
                    l.fill(255f);

                    float x = t * timeScale;

                    float v = chart.getValue(t);
                    if (Float.isNaN(v)) {
                        continue;
                    }
                    float p = v / total;

                    float px = x;
                    float h = p * yScale;

                    float gap = (h*(1.0f - barHeight)/2f);
                    l.fill(ccolor, 255f * (0.5f + 0.5f * p));
                    l.rect(px, sy+gap, timeScale * barWidth, h - gap);

                    sy += h;
                }
            }
        }

    }

    public static class BarChart extends LineChart {

        float barWidth = 0.5f;

        public BarChart(TimeSeries t) {
            super(t);
        }

        public BarChart(NARTrace t, String... sensors) {
            super(t, sensors);
        }

        
        
        @Override
        protected void drawData(Timeline2DCanvas l, float timeScale, float yScale1, float y) {
            int ccolor = 0;
            TimeSeries chart = sensors.get(0);
            ccolor = chart.getColor().getRGB();

            
            
            l.noStroke();
            for (long t = l.cycleStart; t < l.cycleEnd; t++) {
                float x = t * timeScale;

                float v = chart.getValue(t);
                if (Float.isNaN(v)) {
                    continue;
                }

                float p = (max == min) ? 0 : (float) ((v - min) / (max - min));
                float px = x;
                float h = p * yScale1;
                float py = y + yScale1 - h;

                l.fill(ccolor, 255f * (0.5f + 0.5f * p));
                l.rect(px, py, timeScale * barWidth, h);
            }
        }

    }

    public static class EventChart extends Chart {

        private final NARTrace trace;

        //stores the previous "representative event" for an object as the visualization is updated each time step
        public Map<Object, EventPoint> lastSubjectEvent = new HashMap();

        //all events mapped to their visualized feature
        public Map<Object, EventPoint> events = new HashMap();
        private float timeScale;
        private float yScale;
        private Timeline2DCanvas l;
        
        boolean includeTaskEvents = true;
        boolean includeOutputEvents = true;
        boolean includeOtherEvents = true;
        private final TreeMap<Long, List<InferenceEvent>> timepoints;
        

        public static class EventPoint<X> {

            public float x, y, z;
            public final X value;
            public final List<EventPoint<X>> incoming = new ArrayList<>();
            public final Object subject;

            public EventPoint(X value, Object subject, float x, float y, float z) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.subject = subject;
                this.value = value;
            }

            private void set(float x, float y, float z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }

        }

        public EventChart(NARTrace trace, boolean includeTaskEvents, boolean includeOutputEvents, boolean includeOtherEvents) {
            super();
            this.trace = trace;
            this.includeTaskEvents = includeTaskEvents;
            this.includeOutputEvents = includeOutputEvents;
            this.includeOtherEvents = includeOtherEvents;
            timepoints = new TreeMap();
        }

        @Override
        public void update(Timeline2DCanvas l, float timeScale, float yScale) {
            TreeMap<Long, List<InferenceEvent>> time = trace.time;

            synchronized (timepoints) {
                timepoints.putAll(time.subMap(l.cycleStart, l.cycleEnd));
            }
        }
        
        @Override
        public void draw(Timeline2DCanvas l, float y, float timeScale, float yScale) {
            this.timeScale = timeScale;
            this.yScale = yScale * height;
            this.l = l;
            
            if ((timepoints == null) || (timepoints.isEmpty()))
                return;
            
            lastSubjectEvent.clear();
            events.clear();


            l.noStroke();
            l.textSize(l.drawnTextScale);

            synchronized (timepoints) {
                //something not quite right about this
                long maxItemsPerCycle = timepoints.values().stream().map(x -> x.stream().filter(e -> include(e)).count()).max(Long::compare).get();

                for (Map.Entry<Long, List<InferenceEvent>> e : timepoints.entrySet()) {
                    long t = e.getKey();
                    List<InferenceEvent> v = e.getValue();
                    drawEvent(t, v, y, (int)maxItemsPerCycle);
                }
            }

            l.strokeCap(SQUARE);
            l.strokeWeight(2f);
            for (EventPoint<Object> to : events.values()) {
                for (EventPoint<Object> from : to.incoming) {
                    l.stroke(256f * NARSwing.hashFloat(to.subject.hashCode()), 100f, 200f, 68f);
                    l.line(timeScale * from.x, from.y, timeScale * to.x, to.y);
                }
            }

        }

        public boolean include(InferenceEvent i) {
            if (i instanceof TaskEvent) return includeTaskEvents;
            if (i instanceof OutputEvent) return includeOutputEvents;
            return includeOtherEvents;
        }
        
        private void drawEvent(long t, List<InferenceEvent> v, float y, int maxItemsPerCycle) {

            if (v.isEmpty()) return;
            
            float itemScale = Math.min(timeScale/maxItemsPerCycle, yScale/maxItemsPerCycle);

            float x = t;
            
            y += yScale/maxItemsPerCycle/2f;
            
            for (InferenceEvent i : v) {

                if (!include(i))
                    continue;
                
                Class c = i.getType();
                
            //box(2);
                //quad(-0.5f, -0.5f, 0, 0.5f, -0.5f, 0, 0.5f, 0.5f, 0, -0.5f, 0.5f, 0);
                if (i instanceof TaskEvent) {
                    TaskEvent te = (TaskEvent) i;
                    float p = te.priority;

                    {
                        l.fill(256f * NARSwing.hashFloat(c.hashCode()), 200f, 200f);                        

                        switch (te.type) {
                            case Added:
                                //forward
                                triangleHorizontal(i, te.task, p * itemScale, x, y, 1.0f);
                                break;
                            case Removed:
                                //backwards
                                triangleHorizontal(i, te.task, p * itemScale, x, y, -1.0f);
                                break;

                        }

                    }
                } else if (i instanceof OutputEvent) {
                    OutputEvent te = (OutputEvent) i;

                    float p = 0.5f;
                    if (te.signal.length > 0)
                        if (te.signal[0] instanceof Item) {
                            Item ii = (Item) te.signal[0];
                            if (ii.budget!=null)
                                p = ii.getPriority();
                            else
                                p= 0.5f;
                        }
                    float ph = 0.5f + 0.5f * p; //so that priority 0 will still be visible

                    l.fill(256f * NARSwing.hashFloat(te.channel.hashCode()), 100f + 100f * ph, 255f * ph);

                    if (te.channel.equals(IN.class)) {
                        /*pushMatrix();
                         translate(x*timeScale, y*yScale);
                         rotate(0.65f); //angled diagonally down and to the right                    */
                        triangleHorizontal(i, te.signal, ph * itemScale, x, y, 1.0f);
                        //popMatrix();
                    } else if (te.channel.equals(OUT.class)) {
                    //TODO use faster triangleVertical function instead of push and rotate
                    /*pushMatrix();
                         translate(x*timeScale, y*yScale);
                         rotate(MathUtils.HALF_PI); //angled diagonally down and to the right                   */
                        triangleHorizontal(i, te.signal, ph * itemScale, x, y, 1.0f);
                        //popMatrix();
                    } /*else if exe... {
                    
                     }*/ else {
                        rect(i, te.signal, ph * itemScale, x, y);
                    }
                } else {
                    l.fill(256f * NARSwing.hashFloat(c.hashCode()), 200f, 200f);
                    rect(i, null, 0.75f * itemScale, x, y);
                }

                x += 1.0 / v.size();
                y += yScale / maxItemsPerCycle;
            }

        }

        protected void rect(Object event, Object subject, float r, float x, float y/*, float z*/) {
            float px = x * timeScale;
            float py = y;
            
            if (r < 2) r = 2;
            
            l.rect(
                    px + -r / 2f, py + -r / 2f,
                    r, r
            );

            label(event, subject, r, x, y);
        }

        protected void label(Object event, Object subject, float r, float x, float y) {
                        
            if ((l.showItemLabels) && (r * l.drawnTextScale  > l.minLabelScale)) { // && (r * timeScale > l.minLabelScale * l.drawnTextScale)) {
                l.fill(255f);
                l.text(event.toString(), timeScale * x - r / 2, y);
            }

            setEventPoint(event, subject, x, y, 0);
        }

        protected void triangleHorizontal(Object event, Object subject, float r, float x, float y, float direction) {
            float px = x * timeScale;
            float py = y;

            if (r < 2) r = 2;
            
            l.triangle(
                    px + direction * -r / 2, py + direction * -r / 2,
                    px + direction * r / 2, py + 0,
                    px + direction * -r / 2, py + direction * r / 2
            );
            label(event, subject, r, x, y);
        }

        protected void setEventPoint(Object event, Object subject, float x, float y, float z) {
            EventPoint f = new EventPoint(event, subject, x, y, z);
            events.put(event, f);

            if (subject != null) {
                EventPoint e = lastSubjectEvent.put(subject, f);
                if (e != null) {
                    f.incoming.add(e);
                }
            }
        }

    }

    public Timeline2DCanvas(List<Chart> charts) {
        super();
        this.charts = charts;
        init();
    }
    
    public Timeline2DCanvas(Chart... charts) {        
        this(Lists.newArrayList(charts));
    }
    
    public void view(long start, long end) {

        //TODO calculate timeScale and camX to view the cycle range
        
    }
    

    @Override
    public void setup() {
        colorMode(HSB);
        frameRate(defaultFrameRate);        
    }

    @Override
    protected void resizeRenderer(int newWidth, int newHeight) {
        SwingUtilities.invokeLater(new Runnable() {

            @Override public void run() {
                Timeline2DCanvas.super.resizeRenderer(newWidth, newHeight);
                updateNext();
            }
            
        });
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        super.mouseWheelMoved(e);
        /*int wr = e.getWheelRotation();
         camScale += wr * dScale;
         if (wr != 0)
         updateNext();*/
    }

    @Override
    public void keyPressed(KeyEvent event) {
        super.keyPressed(event);
        if (event.getKey() == 'l') {
            showItemLabels = !showItemLabels;
            updateNext();
        }

    }

    protected void updateMouse() {
        
        boolean changed = false;
    //protected void updateCamera() {

        
        //scale limits
        if (mouseButton > 0) {
            long now = System.nanoTime();
            
            if (Float.isFinite(lastMousePressX)) {
                float dt = (now - lastUpdate)/1e9f;
                
                float dx = (mouseX - lastMousePressX);
                float dy = (mouseY - lastMousePressY);

                if (mouseButton == 37) {
                    //left mouse button
                    if ((dx != 0) || (dy != 0)) {
                        camX -= dx;
                        camY -= dy;
                        changed = true;
                    }
                } else if (mouseButton == 39) {
                    //right mouse button
                    float sx = dx * scaleSpeed * dt;
                    float sy = dy * scaleSpeed * dt;         
                    
                    camX += sx * camX / timeScale;
                    camY += sy * camY / yScale;
                    
                    timeScale += sx;
                    yScale += sy;

                    changed = true;
                    //System.out.println(camX +  " " + camY + " " + sx + " "  + sy);
                }
//                else if (mouseButton == 3) {
//                    //middle mouse button (wheel)
//                    rotZ -= dx * rotSpeed;
//                }
            }

            lastMousePressX = mouseX;
            lastMousePressY = mouseY;

            lastUpdate = now;
            
        } else {
            lastMousePressX = Float.NaN;
        }
        
        
        
        
        if (changed) {            
            updateNext();
        }

    }

    protected void updateCamera() {
            
        if (yScale < minYScale) yScale = minYScale;
        if (yScale > maxYScale) yScale = maxYScale;
        if (timeScale < minTimeScale)  timeScale = minTimeScale;
        if (timeScale > maxTimeScale) timeScale = maxTimeScale;

        translate(-camX + width / 2, -camY + height / 2);

        cycleStart = (int) (Math.floor((camX - width / 2) / timeScale) - 1);
        cycleStart = Math.max(0, cycleStart);
        cycleEnd = (int) (Math.ceil((camX + width / 2) / timeScale) + 1);

        if (cycleEnd < cycleStart) cycleEnd = cycleStart;

        drawnTextScale = Math.min(yScale, timeScale) * textScale;
        
    }
    
    public void updateNext() {
        if (!updating) {
            for (Chart c : charts) {
                float h = c.height * yScale;
                c.update(this, timeScale, yScale);
            }            
        }
        updating = true;        
    }

    @Override
    public void draw() {

        if (!isDisplayable() || !isVisible())
            return;
            
        updateMouse();
        updateCamera();

        if (!updating) {
            return;
        }

        updating = false;

        background(0);

        float y = 0;
        float yMargin = yScale * 0.1f;
        for (Chart c : charts) {
            float h = c.height * yScale;
            c.draw(this, y, timeScale, yScale);
            y += (h + yMargin);
        }

    }

    /*
    
    
     import picking.*;

     Picker picker;
     float a = 0.0;

     void setup() {
     size(200, 150, P3D);
     picker = new Picker(this);
     }

     void draw() {
     a += 0.01;

     background(255);

     picker.start(0);
     drawBox(80, 75, 50, #ff8800);

     picker.start(1);
     drawBox(140, 75, 20, #eeee00);

     picker.stop();

     color c = 0;
     int id = picker.get(mouseX, mouseY);
     switch (id) {
     case 0:
     c = #ff8800;
     break;
     case 1:
     c = #eeee00;
     break;
     }
     drawBorder(10, c);
     }

     void drawBox(int x, int y, int w, color c) {
     stroke(0);
     fill(c);
     pushMatrix();
     translate(x, y);
     rotateX(a); rotateY(a);
     box(w);
     popMatrix();
     }

     void drawBorder(int w, color c) {
     noStroke();
     fill(c);
     rect(0,   0, width, w);
     rect(0, height - w, width, w);
     rect(0,   0, w, height);
     rect(width - w, 0, w, height);
     }
     */
}