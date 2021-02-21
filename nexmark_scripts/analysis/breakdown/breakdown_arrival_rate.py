import getopt
import os
import sys
import utilities


def ReadFile(runtime, per_task_rate, parallelism, key_set, per_key_state_size, reconfig_interval, reconfig_type,
             affected_tasks):
    w, h = 5, 4
    y = [[0 for x in range(w)] for y in range(h)]

    i = 0
    for per_task_rate in [1000, 2000, 4000, 6000, 8000]:
        # ${reconfig_type}-${reconfig_interval}-${runtime}-${parallelism}-${per_task_rate}-${key_set}-${per_key_state_size}-${affected_tasks}
        exp = utilities.FILE_FOLER + '/trisk-{}-{}-{}-{}-{}-{}-{}-{}'.format(reconfig_type, reconfig_interval, runtime,
                                                                             parallelism, per_task_rate, key_set,
                                                                             per_key_state_size, affected_tasks)
        file_path = os.path.join(exp, "timer.output")
        try:
            stats = utilities.breakdown(open(file_path).readlines())
            for j in range(4):
                if utilities.timers[j] not in stats:
                    y[j][i] = 0
                else:
                    y[j][i] = stats[utilities.timers[j]]
            i += 1
        except:
            print("Error while processing the file {}".format(exp))

    return y


def draw(val):
    runtime, per_task_rate, parallelism, key_set, per_key_state_size, reconfig_interval, reconfig_type, affected_tasks = val

    # parallelism
    x_values = [1000, 2000, 4000, 6000, 8000]
    y_values = ReadFile(runtime, per_task_rate, parallelism, key_set, per_key_state_size, reconfig_interval,
                        reconfig_type, affected_tasks)

    legend_labels = utilities.legend_labels

    utilities.DrawFigure(x_values, y_values, legend_labels,
                         'rate(e/s)', 'breakdown (ms)',
                         'breakdown_{}_{}'.format(reconfig_type, "rate"), True)

# if __name__ == '__main__':
#     runtime, per_task_rate, parallelism, key_set, per_key_state_size, reconfig_interval, reconfig_type, affected_tasks = utilities.init()
#
#     try:
#         opts, args = getopt.getopt(sys.argv[1:], '-t::h', ['reconfig type', 'help'])
#     except getopt.GetoptError:
#         print('breakdown_parallelism.py -t type')
#         sys.exit(2)
#     for opt, opt_value in opts:
#         if opt in ('-h', '--help'):
#             print("[*] Help info")
#             exit()
#         elif opt == '-t':
#             print('Reconfig Type:', opt_value)
#             reconfig_type = str(opt_value)
#
#     draw()
