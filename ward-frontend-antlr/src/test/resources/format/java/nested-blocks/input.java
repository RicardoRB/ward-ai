class Flow {
void run(List<String> items, int limit) {
for (String item : items) {
if (item.isEmpty()) { continue; } else if (item.length() > limit) {
try {
process(item);
} catch (IOException | RuntimeException e) {
log(e);
} finally {
close();
}
} else {
while (ready()) { step(); }
}
}
switch (limit) {
case 1: handle(); break;
case 2: {
handle();
break;
}
default:
stop();
}
Runnable task = () -> {
execute();
};
}
}
