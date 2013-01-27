package com.graphhopper.routing.ch;

import com.graphhopper.routing.DijkstraSimple;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.ch.PrepareContractionHierarchies.NodeCH;
import com.graphhopper.routing.ch.PrepareContractionHierarchies.Shortcut;
import com.graphhopper.routing.util.CarStreetType;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.LevelGraph;
import com.graphhopper.storage.LevelGraphStorage;
import com.graphhopper.util.EdgeSkipIterator;
import com.graphhopper.util.GraphUtility;
import gnu.trove.list.array.TIntArrayList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class PrepareContractionHierarchiesTest {

	LevelGraph createGraph() {
		return new LevelGraphStorage().createNew(100);
	}

	LevelGraph createExampleGraph() {
		LevelGraph g = createGraph();

		//5-1-----2
		//   \ __/|
		//    0   |
		//   /    |
		//  4-----3
		//
		g.edge(0, 1, 1, true);
		g.edge(0, 2, 1, true);
		g.edge(0, 4, 3, true);
		g.edge(1, 2, 2, true);
		g.edge(2, 3, 1, true);
		g.edge(4, 3, 2, true);
		g.edge(5, 1, 2, true);
		return g;
	}

	List<NodeCH> createGoals(int... gNodes) {
		List<NodeCH> goals = new ArrayList<NodeCH>();
		for (int gNode : gNodes) {
			NodeCH n = new NodeCH();
			n.endNode = gNode;
			goals.add(n);
		}
		return goals;
	}

	@Test
	public void testShortestPathSkipNode() {
		LevelGraph g = createExampleGraph();
		double normalDist = new DijkstraSimple(g).calcPath(4, 2).distance();
		PrepareContractionHierarchies.OneToManyDijkstraCH algo = new PrepareContractionHierarchies.OneToManyDijkstraCH(g)
				.setFilter(new PrepareContractionHierarchies.EdgeLevelFilterCH(g).setAvoidNode(3));
		List<NodeCH> gs = createGoals(2);
		algo.setLimit(10).calcPath(4, gs);
		Path p = algo.extractPath(gs.get(0).entry);
		assertTrue(p.distance() > normalDist);
	}

	@Test
	public void testShortestPathSkipNode2() {
		LevelGraph g = createExampleGraph();
		double normalDist = new DijkstraSimple(g).calcPath(4, 2).distance();
		PrepareContractionHierarchies.OneToManyDijkstraCH algo = new PrepareContractionHierarchies.OneToManyDijkstraCH(g).
				setFilter(new PrepareContractionHierarchies.EdgeLevelFilterCH(g).setAvoidNode(3));
		List<NodeCH> gs = createGoals(1, 2);
		algo.setLimit(10).calcPath(4, gs);
		Path p = algo.extractPath(gs.get(1).entry);
		assertTrue(p.distance() > normalDist);
	}

	@Test
	public void testShortestPathLimit() {
		LevelGraph g = createExampleGraph();
		PrepareContractionHierarchies.OneToManyDijkstraCH algo = new PrepareContractionHierarchies.OneToManyDijkstraCH(g)
				.setFilter(new PrepareContractionHierarchies.EdgeLevelFilterCH(g).setAvoidNode(0));
		List<NodeCH> gs = createGoals(1);
		algo.setLimit(2).calcPath(4, gs);
		assertNull(gs.get(0).entry);
	}

	@Test
	public void testAddShortcuts() {
		LevelGraph g = createExampleGraph();
		int old = GraphUtility.count(g.allEdges());
		PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().setGraph(g);
		prepare.doWork();
		assertEquals(old, GraphUtility.count(g.allEdges()));
	}

	@Test
	public void testDirectedGraph() {
		LevelGraph g = createGraph();
		g.edge(5, 4, 3, false);
		g.edge(4, 5, 10, false);
		g.edge(2, 4, 1, false);
		g.edge(5, 2, 1, false);
		g.edge(3, 5, 1, false);
		g.edge(4, 3, 1, false);
		int old = GraphUtility.count(g.allEdges());
		PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().setGraph(g);
		prepare.doWork();
		// PrepareTowerNodesShortcutsTest.printEdges(g);
		assertEquals(old + 2, GraphUtility.count(g.allEdges()));
		RoutingAlgorithm algo = prepare.createAlgo();
		Path p = algo.calcPath(4, 2);
		assertEquals(3, p.distance(), 1e-6);
		assertEquals(new TIntArrayList(new int[]{4, 3, 5, 2}), p.calcNodes());
	}

	@Test
	public void testDirectedGraph3() {
		LevelGraph g = createGraph();
		g.edge(0, 2, 2, true);
		g.edge(10, 2, 2, true);
		g.edge(11, 2, 2, true);
		// create a longer one directional edge => no longish one-dir shortcut should be created
		g.edge(2, 1, 2, true);
		g.edge(2, 1, 10, false);

		g.edge(1, 3, 2, true);
		g.edge(3, 4, 2, true);
		g.edge(3, 5, 2, true);
		g.edge(3, 6, 2, true);
		g.edge(3, 7, 2, true);

		PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().setGraph(g);
		prepare.initFromGraph();
		// find all shortcuts if we contract node 1
		Collection<Shortcut> scs = prepare.findShortcuts(1);
		assertEquals(2, scs.size());
		Iterator<Shortcut> iter = scs.iterator();
		Shortcut sc1 = iter.next();
		Shortcut sc2 = iter.next();
		if (sc1.distance > sc2.distance) {
			Shortcut tmp = sc1;
			sc1 = sc2;
			sc2 = tmp;
		}

		assertTrue(sc1.toString(), sc1.from == 2 && sc1.to == 3);
		assertTrue(sc2.toString(), sc2.from == 2 && sc2.to == 3);

		assertEquals(sc1.toString(), 4, sc1.distance, 1e-4);
		assertEquals(sc2.toString(), 12, sc2.distance, 1e-4);
	}

	void initRoundaboutGraph(Graph g) {
		//              roundabout:
		//16-0-9-10--11   12<-13
		//    \       \  /      \
		//    17       \|        7-8-..
		// -15-1--2--3--4       /     /
		//     /         \-5->6/     /
		//  -14            \________/

		g.edge(16, 0, 1, true);
		g.edge(0, 9, 1, true);
		g.edge(0, 17, 1, true);
		g.edge(9, 10, 1, true);
		g.edge(10, 11, 1, true);
		g.edge(11, 28, 1, true);
		g.edge(28, 29, 1, true);
		g.edge(29, 30, 1, true);
		g.edge(30, 31, 1, true);
		g.edge(31, 4, 1, true);

		g.edge(17, 1, 1, true);
		g.edge(15, 1, 1, true);
		g.edge(14, 1, 1, true);
		g.edge(14, 18, 1, true);
		g.edge(18, 19, 1, true);
		g.edge(19, 20, 1, true);
		g.edge(20, 15, 1, true);
		g.edge(19, 21, 1, true);
		g.edge(21, 16, 1, true);
		g.edge(1, 2, 1, true);
		g.edge(2, 3, 1, true);
		g.edge(3, 4, 1, true);

		g.edge(4, 5, 1, false);
		g.edge(5, 6, 1, false);
		g.edge(6, 7, 1, false);
		g.edge(7, 13, 1, false);
		g.edge(13, 12, 1, false);
		g.edge(12, 4, 1, false);

		g.edge(7, 8, 1, true);
		g.edge(8, 22, 1, true);
		g.edge(22, 23, 1, true);
		g.edge(23, 24, 1, true);
		g.edge(24, 25, 1, true);
		g.edge(25, 27, 1, true);
		g.edge(27, 5, 1, true);
		g.edge(25, 26, 1, false);
		g.edge(26, 25, 1, false);
	}

	@Test
	public void testRoundaboutUnpacking() {
		LevelGraph g = createGraph();
		initRoundaboutGraph(g);
		int old = GraphUtility.count(g.allEdges());
		PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().setGraph(g);
		prepare.doWork();
		// PrepareTowerNodesShortcutsTest.printEdges(g);
		assertEquals(old + 19, GraphUtility.count(g.allEdges()));
		RoutingAlgorithm algo = prepare.createAlgo();
		Path p = algo.calcPath(4, 7);
		assertEquals(new TIntArrayList(new int[]{4, 5, 6, 7}), p.calcNodes());
	}

	@Test
	public void testFindShortcuts_Roundabout() {
		LevelGraphStorage g = (LevelGraphStorage) createGraph();
		EdgeSkipIterator iter = g.edge(1, 3, 1, true);
		g.edge(3, 4, 1, true);
		EdgeSkipIterator iter2 = g.edge(4, 5, 1, false);
		g.edge(5, 6, 1, false);
		g.edge(6, 7, 1, true);
		EdgeSkipIterator iter3 = g.edge(6, 8, 2, false);
		g.edge(8, 4, 1, false);
		g.setLevel(3, 3);
		g.setLevel(5, 5);
		g.setLevel(7, 7);
		g.setLevel(8, 8);

		g.edge(1, 4, 2, PrepareContractionHierarchies.scBothDir).setSkippedEdge(iter.edge());
		int f = PrepareContractionHierarchies.scOneDir;
		g.edge(4, 6, 2, f).setSkippedEdge(iter2.edge());
		g.edge(6, 4, 3, f).setSkippedEdge(iter3.edge());

		PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().setGraph(g).initFromGraph();
		// there should be two different shortcuts for both directions!
		Collection<Shortcut> sc = prepare.findShortcuts(4);
		assertEquals(2, sc.size());
	}

	void initUnpackingGraph(LevelGraphStorage g) {
		double dist = 1;
		int flags = CarStreetType.flags(30, false);
		g.edge(10, 0, dist, flags);
		EdgeSkipIterator iter = g.edge(0, 1, dist, flags);
		g.edge(1, 2, dist, flags);
		g.edge(2, 3, dist, flags);
		g.edge(3, 4, dist, flags);
		g.edge(4, 5, dist, flags);
		g.edge(5, 6, dist, flags);
		int f = PrepareContractionHierarchies.scOneDir;

		int tmp = iter.edge();
		iter = g.edge(0, 2, 2, f);
		iter.setSkippedEdge(tmp);
		tmp = iter.edge();
		iter = g.edge(0, 3, 3, f);
		iter.setSkippedEdge(tmp);
		tmp = iter.edge();
		iter = g.edge(0, 4, 4, f);
		iter.setSkippedEdge(tmp);
		tmp = iter.edge();
		iter = g.edge(0, 5, 5, f);
		iter.setSkippedEdge(tmp);
		tmp = iter.edge();
		iter = g.edge(0, 6, 6, f);
		iter.setSkippedEdge(tmp);
		g.setLevel(0, 10);
		g.setLevel(6, 9);
		g.setLevel(5, 8);
		g.setLevel(4, 7);
		g.setLevel(3, 6);
		g.setLevel(2, 5);
		g.setLevel(1, 4);
		g.setLevel(10, 3);
	}

	@Test
	public void testUnpackingOrder() {
		LevelGraphStorage g = (LevelGraphStorage) createGraph();
		initUnpackingGraph(g);
		PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().setGraph(g);
		RoutingAlgorithm algo = prepare.createAlgo();
		Path p = algo.calcPath(10, 6);
		assertEquals(7, p.distance(), 1e-5);
		assertEquals(new TIntArrayList(new int[]{10, 0, 1, 2, 3, 4, 5, 6}), p.calcNodes());
	}

	@Test
	public void testUnpackingOrder_Fastest() {
		LevelGraphStorage g = (LevelGraphStorage) createGraph();
		PrepareContractionHierarchies prepare = new PrepareContractionHierarchies().setGraph(g);
		initUnpackingGraph(g);
		RoutingAlgorithm algo = prepare.createAlgo();
		Path p = algo.calcPath(10, 6);
		assertEquals(7, p.distance(), 1e-1);
		assertEquals(new TIntArrayList(new int[]{10, 0, 1, 2, 3, 4, 5, 6}), p.calcNodes());
	}
}
