package edu.tum.cs.vis;

import java.util.PriorityQueue;
import java.util.Vector;

import edu.tum.cs.tools.Vector3f;

public class Trajectory implements Drawable, DrawableAnimated {

	public Vector<Point> points;
	public float pointSize = 40.0f, sphereSize = 120.0f;
	public int pointColor = 0xffcbcbcb, sphereColor = 0xffffff00; 
	public float minx, miny, minz, maxx, maxy, maxz;
	public float range;
	
	public Trajectory() {
		points = new Vector<Point>();
		resetStats();
	}
	
	protected void resetStats() {
		minx = Float.MAX_VALUE; miny = Float.MAX_VALUE; minz = Float.MAX_VALUE; 
		maxx = Float.MIN_VALUE; maxy = Float.MIN_VALUE; maxz = Float.MIN_VALUE;
		range = 0;
	}
	
	protected void updateStats(float x, float y, float z) {
		minx = Math.min(x, minx);
		miny = Math.min(y, miny);
		minz = Math.min(z, minz);
		maxx = Math.max(x, maxx);
		maxy = Math.max(y, maxy);
		maxz = Math.max(z, maxz);
		float xrange = maxx - minx;
		float yrange = maxy - miny;
		float zrange = maxz - minz;
		range = Math.max(Math.max(xrange, yrange), zrange);
	}
	
	public void addPoint(float x, float y, float z) {
		points.add(new Point(x, y, z, pointColor, pointSize));
		updateStats(x,y,z);
	}
	
	public float getMaxAbsCoord() {
		float x = Math.max(Math.abs(minx), Math.abs(maxx));
		float y = Math.max(Math.abs(miny), Math.abs(maxy));
		float z = Math.max(Math.abs(minz), Math.abs(maxz));
		float xy = Math.max(x, y);
		return Math.max(xy, z);
	}
	
	public void draw(Canvas c, int step) {
		c.pushMatrix();
		pointSize = range / 150;
		sphereSize = pointSize * 2;
		//System.out.println(pointSize);
		Point prev = null;
		int s = 0;
		for(Point p : points) {
			p.size = pointSize;
			if(s++ > step)
				break;
			p.draw(c);
			if(prev != null) { // draw line connecting previous point with current point
				c.stroke(255,255,255);
				//System.out.printf("%f %f %f -> %f %f %f\n",prev.x, prev.y, prev.z, p.x, p.y, p.z);
				c.line(prev.v.x, prev.v.y, prev.v.z, p.v.x, p.v.y, p.v.z);
			}
			prev = p;
		}
		
		// draw sphere for current pos
		new Sphere(prev.v.x, prev.v.y, prev.v.z, sphereSize, sphereColor).draw(c);
		
		//c.eyeTarget.set(prev.v);
		c.popMatrix();
	}
	
	public int getNumSteps() {
		return points.size();
	}
	
	public void draw(Canvas c) {
		draw(c, getNumSteps()-1);
	}

	public void segment() {
		int i = 0;
		java.util.PriorityQueue<Double> min_distances = new PriorityQueue<Double>();
		for(Point p : points) {
			
			// check previous points for proximity			
			if(i >= 3) {				 
				double min_distance = Double.MAX_VALUE;
				int min_distance_point_idx = -1;
				for(int j = i-3; j >= 0; j--) {
					Point p2 = points.get(j);
					double dist = p.v.distance(p2.v);
					if(dist < min_distance) {
						min_distance = dist;
						min_distance_point_idx = j;
					}
				}
				min_distances.add(min_distance);
				
				// merge if distance to closest previous point is small enough
				if(min_distance < 20) {
					Point p2 = points.get(min_distance_point_idx);
					
					// ... and directions are similar
					boolean dirSimilar = false;
					if(min_distance_point_idx == 0)
						dirSimilar = true;
					else {
						Vector3f dir1 = new Vector3f(p.v);
						dir1.subtract(points.get(i-1).v);
						Vector3f dir2 = new Vector3f(p2.v);
						dir2.subtract(points.get(min_distance_point_idx-1).v);
						double angle = dir1.angle(dir2);
						System.out.println("angle = " + angle * 180/Math.PI);
						dirSimilar = angle < Math.PI*40/180;
					}
					
					if(dirSimilar) { // merge p and p2
						p.copyPos(p2);
						p.color = 0xffff0000;
						p2.color = 0xffff0000;
					}
				}					
			}
			
			i++;
		}
		
		// print minimum distances
		Double prev_d = null;
		double max_diff = Double.MIN_VALUE;
		double max_diff_value = 0;
		while(!min_distances.isEmpty()) {
			double d = min_distances.remove();
			if(prev_d != null) {
				double diff = d-prev_d;
				if(diff > max_diff) {
					max_diff = diff;
					max_diff_value = d;
				}
			}
			prev_d = d;
			//System.out.println(d);
		}
		System.out.println("max diff: " + max_diff + " @ " + max_diff_value);
		
	}
	
	/**
	 * centers the trajectory around the mean position
	 */
	public void center() {
		// determine mean point
		float x = 0, y = 0, z = 0;
		for(Point p : points) {
			x += p.v.x;
			y += p.v.y;
			z += p.v.z;
		}
		x /= points.size();
		y /= points.size();
		z /= points.size();
		Vector3f mean = new Vector3f(x, y, z);
		// subtract mean from all points and update stats
		resetStats();
		for(Point p : points) { 
			p.v.sub(mean);
			updateStats(p.v.x, p.v.y, p.v.z);
		}
	}

	public int getMaxStep() {
		return getNumSteps()-1;
	}
}
