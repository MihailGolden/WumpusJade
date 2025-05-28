package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;

import java.util.*;

public class EnvironmentAgent extends Agent {
    private final int size = 4;
    private boolean[][] pits;
    private boolean[][] wumpus;
    private boolean[][] gold;
    private int agentX, agentY;
    private String agentDir;
    private int timeTick;
    private boolean bumpFlag = false;
    private boolean screamFlag = false;

    @Override
    protected void setup() {
        // 0) Зчитаємо параметр worldId з аргументів
        Object[] args = getArguments();
        int worldId = 1;
        if (args != null && args.length > 0) {
            try { worldId = Integer.parseInt((String) args[0]); }
            catch(Exception e) { /* залишаємо 1 */ }
        }
        System.out.println("[Environment] Initializing world #" + worldId);
        initWorld(worldId);


        // 1) Ініціалізація поля
        pits   = new boolean[size][size];
        wumpus = new boolean[size][size];
        gold   = new boolean[size][size];
        pits[1][3]    = true;
        pits[2][1]    = true;
        wumpus[2][2]  = true;
        gold[0][2]    = true;

        agentX   = 0;
        agentY   = 0;
        agentDir = "EAST";
        timeTick = 0;

        // 2) Реєстрація в DF
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("WumpusEnvironment");
        sd.setName(getLocalName());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 3) Поведінка: обробляємо REQUEST і CFP
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    System.out.println("[Environment] Message received: " + msg.getContent());
                    switch (msg.getPerformative()) {
                        case ACLMessage.REQUEST:
                            System.out.println("[Environment] Processing REQUEST");
                            addBehaviour(new OneShotBehaviour() {
                                @Override
                                public void action() {
                                    String p = buildPercepts();
                                    ACLMessage reply = msg.createReply();
                                    reply.setPerformative(ACLMessage.INFORM);
                                    reply.setContent("Percepts" + p + "," + timeTick);
                                    System.out.println("[Environment] Sending percepts: " + reply.getContent());
                                    timeTick++;
                                    send(reply);
                                }
                            });
                            break;
                        case ACLMessage.CFP:
                            System.out.println("[Environment] Processing CFP");
                            String content = msg.getContent();
                            addBehaviour(new OneShotBehaviour() {
                                @Override
                                public void action() {
                                    String act = parseAction(content);
                                    applyAction(act);
                                    ACLMessage reply = msg.createReply();
                                    reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                                    reply.setContent("OK");
                                    System.out.println("[Environment] Action applied: " + act);
                                    send(reply);
                                }
                            });
                            break;
                        default:
                            System.out.println("[Environment] Unknown performative: " + msg.getPerformative());
                            break;
                    }
                } else {
                    block();
                }
            }
        });
    }

    private String buildPercepts() {
        List<String> p = new ArrayList<>();
        if (wumpus[agentX][agentY]) p.add("Stench");
        if (pits[agentX][agentY])   p.add("Breeze");
        if (gold[agentX][agentY])   p.add("Glitter");
        if (bumpFlag)               p.add("Bump");
        if (screamFlag)             p.add("Scream");

        // Після формування перцептів — скидати прапорці
        bumpFlag = false;
        screamFlag = false;

        return p.toString();
    }


    // Розбираємо "Action(Forward)" → "Forward"
    private String parseAction(String content) {
        if (content.startsWith("Action(") && content.endsWith(")")) {
            return content.substring(7, content.length() - 1);
        }
        return content;
    }

    // Застосування дії
    private void applyAction(String action) {
        switch (action) {
            case "Forward":   moveForward(); break;
            case "TurnLeft":  turnLeft();    break;
            case "TurnRight": turnRight();   break;
            case "Grab":      grabGold();    break;
            case "Shoot":     shoot();       break;
            case "Climb":     doDelete();    break;  // вихід
            default: break;
        }
    }

    private void moveForward() {
        int nx = agentX, ny = agentY;
        switch (agentDir) {
            case "EAST":  nx++; break;
            case "WEST":  nx--; break;
            case "NORTH": ny++; break;
            case "SOUTH": ny--; break;
        }
        if (nx >= 0 && nx < size && ny >= 0 && ny < size) {
            agentX = nx;
            agentY = ny;
        } else {
            // зіткнення зі стіною
            bumpFlag = true;
        }
    }


    private void turnLeft() {
        agentDir = switch (agentDir) {
            case "EAST"  -> "NORTH";
            case "NORTH" -> "WEST";
            case "WEST"  -> "SOUTH";
            case "SOUTH" -> "EAST";
            default      -> agentDir;
        };
    }

    private void turnRight() {
        agentDir = switch (agentDir) {
            case "EAST"  -> "SOUTH";
            case "SOUTH" -> "WEST";
            case "WEST"  -> "NORTH";
            case "NORTH" -> "EAST";
            default      -> agentDir;
        };
    }

    private void grabGold() {
        if (gold[agentX][agentY]) {
            gold[agentX][agentY] = false;
        }
    }

    private void shoot() {
        int tx = agentX, ty = agentY;
        switch (agentDir) {
            case "EAST":  tx++; break;
            case "WEST":  tx--; break;
            case "NORTH": ty++; break;
            case "SOUTH": ty--; break;
        }
        if (tx >= 0 && tx < size && ty >= 0 && ty < size) {
            if (wumpus[tx][ty]) {
                // влучили у Wumpusa
                wumpus[tx][ty] = false;
                screamFlag = true;
            }
        }
    }

    private void initWorld(int id) {
        // Очистити всі масиви
        pits = new boolean[size][size];
        wumpus = new boolean[size][size];
        gold = new boolean[size][size];

        switch (id) {
            case 1:
                // **World 1** — той, що зараз у вас:
                pits[1][3] = true;
                pits[2][1] = true;
                wumpus[2][2] = true;
                gold[0][2] = true;
                break;
            case 2:
                // **World 2** — інша розстановка:
                pits[0][2] = true;
                pits[3][1] = true;
                wumpus[1][1] = true;
                gold[3][3] = true;
                break;
            case 3:
                // **World 3** — ще інша:
                pits[2][0] = true;
                pits[2][3] = true;
                wumpus[0][3] = true;
                gold[2][2] = true;
                break;
            default:
                // можна додати world 4, 5…
                initWorld(1);
        }
        agentX   = 0; agentY = 0; agentDir = "EAST";
        timeTick = 0;
        bumpFlag   = false; screamFlag = false;
    }
}
